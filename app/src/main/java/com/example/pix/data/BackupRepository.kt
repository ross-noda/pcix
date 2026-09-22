package com.example.pix.data

import android.content.Context
import android.database.Cursor
import android.graphics.BitmapFactory
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pix.domain.RecurrenceRule
import com.example.pix.domain.TaskRules
import com.example.pix.domain.TaskTiming
import java.io.*
import java.util.zip.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Versioned portable archive. Validation happens in an isolated database before replacement. */
class BackupRepository(private val context: Context, private val database: PixDatabase) {
    companion object {
        val tables =
            listOf(
                "lists",
                "tasks",
                "tags",
                "recurring_series",
                "task_tags",
                "subtasks",
                "task_images",
                "reminder_receipts",
            )
        private const val LIMIT = 512L * 1024 * 1024
    }

    class Prepared
    internal constructor(
        internal val directory: File,
        internal val data: JSONObject,
        val tasks: Int,
        val lists: Int,
        val tags: Int,
    ) : Closeable {
        override fun close() {
            directory.deleteRecursively()
        }
    }

    private val images = ImageStore(context)

    suspend fun export(output: OutputStream) =
        withContext(Dispatchers.IO) {
            val data =
                database.withTransaction {
                    val sql = database.openHelper.writableDatabase
                    JSONObject()
                        .put("format", "pcix-backup")
                        .put("version", 1)
                        .put("schema", 6)
                        .put(
                            "tables",
                            JSONObject().apply {
                                tables.forEach { table ->
                                    sql.query("SELECT * FROM $table").use { cursor ->
                                        val rows = JSONArray()
                                        while (cursor.moveToNext()) {
                                            val row = JSONObject()
                                            cursor.columnNames.forEachIndexed { i, name ->
                                                row.put(
                                                    name,
                                                    when (cursor.getType(i)) {
                                                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                                        Cursor.FIELD_TYPE_INTEGER ->
                                                            cursor.getLong(i)
                                                        Cursor.FIELD_TYPE_STRING ->
                                                            cursor.getString(i)
                                                        else -> error("Unsupported value")
                                                    },
                                                )
                                            }
                                            rows.put(row)
                                        }
                                        put(table, rows)
                                    }
                                }
                            },
                        )
                }
            require(
                tables.sumOf { data.getJSONObject("tables").getJSONArray(it).length() } <= 100000
            )
            val bytes = data.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= 16 * 1024 * 1024)
            var total = bytes.size.toLong()
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(bytes)
                zip.closeEntry()
                val names =
                    data
                        .getJSONObject("tables")
                        .getJSONArray("task_images")
                        .objects()
                        .map { it.getString("fileName") }
                        .toSet()
                names.forEach { name ->
                    require(safeName(name))
                    val file = images.file(name)
                    require(file.isFile && file.length() <= 50L * 1024 * 1024)
                    total += file.length()
                    require(total <= LIMIT)
                    zip.putNextEntry(ZipEntry("images/$name"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

    suspend fun prepare(input: InputStream): Prepared =
        withContext(Dispatchers.IO) {
            val directory = File(context.cacheDir, "restore-${newId()}").apply { mkdirs() }
            try {
                val names = mutableSetOf<String>()
                var total = 0L
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name
                        require(!entry.isDirectory && names.add(name) && names.size <= 100001)
                        require(
                            name == "data.json" ||
                                (name.startsWith("images/") &&
                                    safeName(name.removePrefix("images/")))
                        )
                        val file = File(directory, name)
                        file.parentFile!!.mkdirs()
                        val max = if (name == "data.json") 16L * 1024 * 1024 else 50L * 1024 * 1024
                        var size = 0L
                        file.outputStream().use { out ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                size += read
                                total += read
                                require(size <= max && total <= LIMIT)
                                out.write(buffer, 0, read)
                            }
                        }
                        zip.closeEntry()
                    }
                }
                val data = JSONObject(File(directory, "data.json").readText())
                require(
                    data.getString("format") == "pcix-backup" &&
                        data.getInt("version") == 1 &&
                        data.getInt("schema") in 5..6
                )
                val all = data.getJSONObject("tables")
                require(all.keys().asSequence().toSet() == tables.toSet())
                require(tables.sumOf { all.getJSONArray(it).length() } <= 100000)
                val imageNames =
                    all.getJSONArray("task_images")
                        .objects()
                        .map { it.getString("fileName") }
                        .toSet()
                require(names == imageNames.map { "images/$it" }.toSet() + "data.json")
                imageNames.forEach { name ->
                    require(safeName(name))
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(File(directory, "images/$name").path, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0)
                }
                val check = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
                try {
                    check.withTransaction {
                        insert(check.openHelper.writableDatabase, all)
                        validate(check)
                    }
                } finally {
                    check.close()
                }
                Prepared(
                    directory,
                    data,
                    all.getJSONArray("tasks").objects().count {
                        it.getInt("isTemplate") == 0 && it.getInt("isSkipped") == 0
                    },
                    all.getJSONArray("lists").length(),
                    all.getJSONArray("tags").length(),
                )
            } catch (error: Throwable) {
                directory.deleteRecursively()
                throw error
            }
        }

    suspend fun restore(prepared: Prepared) =
        withContext(Dispatchers.IO) {
            val all = JSONObject(prepared.data.getJSONObject("tables").toString())
            val copied = mutableListOf<File>()
            try {
                val names = mutableMapOf<String, String>()
                all.getJSONArray("task_images").objects().forEach { row ->
                    val old = row.getString("fileName")
                    val name =
                        names.getOrPut(old) {
                            val fresh = newId() + ".image"
                            val dest = images.file(fresh)
                            copied.add(dest)
                            File(prepared.directory, "images/$old").copyTo(dest)
                            fresh
                        }
                    row.put("fileName", name)
                }
                database.withTransaction {
                    val sql = database.openHelper.writableDatabase
                    tables.asReversed().forEach { sql.execSQL("DELETE FROM $it") }
                    insert(sql, all)
                    validate(database)
                }
            } catch (error: Throwable) {
                copied.forEach { it.delete() }
                throw error
            } finally {
                prepared.close()
            }
        }

    private fun insert(sql: SupportSQLiteDatabase, all: JSONObject) {
        tables.forEach { table ->
            val columns = linkedMapOf<String, String>()
            sql.query("PRAGMA table_info($table)").use { c ->
                while (c.moveToNext()) columns[c.getString(1)] = c.getString(2)
            }
            val statement =
                sql.compileStatement(
                    "INSERT INTO $table (${columns.keys.joinToString(",")}) VALUES (${columns.keys.joinToString(",") { "?" }})"
                )
            try {
                all.getJSONArray(table).objects().forEach { row ->
                    require(row.keys().asSequence().all { it in columns.keys })
                    statement.clearBindings()
                    columns.entries.forEachIndexed { index, (column, type) ->
                        val v = if (row.has(column)) row.get(column) else JSONObject.NULL
                        when {
                            v == JSONObject.NULL && type == "INTEGER" ->
                                statement.bindLong(index + 1, 0)
                            v == JSONObject.NULL -> statement.bindNull(index + 1)
                            type == "INTEGER" -> {
                                require(v is Int || v is Long)
                                statement.bindLong(index + 1, (v as Number).toLong())
                            }
                            type == "TEXT" -> {
                                require(v is String)
                                statement.bindString(index + 1, v)
                            }
                            else -> error("Unsupported column")
                        }
                    }
                    statement.executeInsert()
                }
            } finally {
                statement.close()
            }
        }
    }

    private suspend fun validate(db: PixDatabase) {
        val sql = db.openHelper.writableDatabase
        sql.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) }
        sql.query("SELECT id FROM lists WHERE id='$INBOX_ID'").use { require(it.moveToFirst()) }
        sql.query(
                "SELECT id FROM tasks WHERE isCompleted NOT IN (0,1) OR isTemplate NOT IN (0,1) OR isSkipped NOT IN (0,1) OR matrixUrgent NOT IN (0,1) OR matrixImportant NOT IN (0,1)"
            )
            .use { require(!it.moveToFirst()) }
        sql.query("SELECT id FROM subtasks WHERE isCompleted NOT IN (0,1)").use {
            require(!it.moveToFirst())
        }
        for (table in listOf("lists", "tags")) sql.query(
                "SELECT id FROM $table WHERE color NOT BETWEEN 0 AND 11"
            )
            .use { require(!it.moveToFirst()) }
        sql.query(
                "SELECT id FROM recurring_series WHERE anchorDay NOT BETWEEN -719162 AND 2932896 OR endBefore NOT BETWEEN -719162 AND 2932896"
            )
            .use { require(!it.moveToFirst()) }
        sql.query("SELECT id FROM tasks").use { c ->
            while (c.moveToNext()) {
                val task = requireNotNull(db.dao().task(c.getString(0)))
                require(TaskRules.validTitle(task.title) && task.priority in setOf(0, 1, 3, 5))
                require(
                    task.minuteOfDay == null || (task.dueDay != null && task.minuteOfDay in 0..1439)
                )
                TaskTiming.validate(task)
                task.dueDay?.let { require(it in -719162..2932896) }
                require((task.seriesId == null) == (task.originalDay == null))
                task.seriesId?.let { require(db.dao().series(it) != null) }
            }
        }
        sql.query("SELECT * FROM recurring_series").use { c ->
            while (c.moveToNext()) {
                RecurrenceRule.parse(c.getString(c.getColumnIndexOrThrow("rule")))
                val template =
                    requireNotNull(
                        db.dao().task(c.getString(c.getColumnIndexOrThrow("templateTaskId")))
                    )
                require(template.isTemplate && template.dueDay != null)
            }
        }
        sql.query("SELECT name, normalizedName FROM tags").use { c ->
            while (c.moveToNext()) require(
                c.getString(0).isNotBlank() &&
                    TaskRules.normalizedTag(c.getString(0)) == c.getString(1)
            )
        }
        for (table in listOf("lists", "subtasks")) sql.query(
                "SELECT ${if(table == "lists") "name" else "title"} FROM $table"
            )
            .use { c -> while (c.moveToNext()) require(c.getString(0).isNotBlank()) }
    }

    private fun safeName(name: String) = name.matches(Regex("[A-Za-z0-9_-]+\\.image"))

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
