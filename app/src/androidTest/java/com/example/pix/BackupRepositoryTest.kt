package com.example.pix

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.*
import java.io.*
import java.time.LocalDate
import java.util.zip.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class BackupRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PixDatabase
    private lateinit var repo: TaskRepository
    private lateinit var backups: BackupRepository
    private val files = mutableListOf<File>()

    @Before
    fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        repo = TaskRepository(db)
        repo.initialize()
        backups = BackupRepository(context, db)
    }

    @After
    fun close() {
        db.close()
        files.forEach { it.delete() }
    }

    private suspend fun archive(): ByteArray =
        ByteArrayOutputStream().also { backups.export(it) }.toByteArray()

    private fun zip(data: String, extra: String? = null): ByteArray =
        ByteArrayOutputStream()
            .also { out ->
                ZipOutputStream(out).use { z ->
                    z.putNextEntry(ZipEntry("data.json"))
                    z.write(data.toByteArray())
                    z.closeEntry()
                    extra?.let {
                        z.putNextEntry(ZipEntry(it))
                        z.write(byteArrayOf(1))
                        z.closeEntry()
                    }
                }
            }
            .toByteArray()

    private fun json(bytes: ByteArray): JSONObject =
        ZipInputStream(bytes.inputStream()).use { z ->
            z.nextEntry
            JSONObject(z.readBytes().toString(Charsets.UTF_8))
        }

    private suspend fun rejected(bytes: ByteArray) {
        try {
            backups.prepare(bytes.inputStream()).close()
            fail("Invalid archive accepted")
        } catch (_: IllegalArgumentException) {} catch (
            _: android.database.sqlite.SQLiteException) {} catch (_: ZipException) {}
    }

    @Test
    fun roundTripIncludesRelationsImagesDurationHistoryAndRepeats() = runBlocking {
        val list = ListEntity(name = "Viaggi", icon = "✈️", color = 3)
        repo.saveList(list)
        val tag = repo.saveTag("Estate", 2)
        val task =
            TaskEntity(
                title = "Weekend",
                notes = "Note complete",
                listId = list.id,
                dueDay = LocalDate.now().toEpochDay(),
                durationMinutes = 2880,
                priority = 1,
            )
        repo.create(task, setOf(tag))
        repo.saveSubtask(SubtaskEntity(taskId = task.id, title = "Valigia", isCompleted = true))
        val file = ImageStore(context).file(newId() + ".image")
        files.add(file)
        file.outputStream().use {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        db.dao().insertImage(TaskImage(taskId = task.id, fileName = file.name))
        repo.editRecurring(
            task,
            setOf(tag),
            RecurrenceRule(Frequency.WEEKLY).encode(),
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.complete(task.id, true)
        db.dao().saveReceipt(ReminderReceipt(task.id, 123L))
        val bytes = archive()
        val extra = TaskEntity(title = "Da sostituire")
        repo.create(extra)
        val prepared = backups.prepare(bytes.inputStream())
        assertEquals(2, prepared.tasks)
        assertEquals(2, prepared.lists)
        assertEquals(1, prepared.tags)
        db.syncDao().clear()
        backups.restore(prepared)
        assertNull(repo.details(extra.id))
        assertEquals(
            "DELETE",
            db.syncDao().pendingFor("tasks", extra.id).single().operation,
        )
        assertTrue(
            db.syncDao().pending().any {
                it.entityType == "task_images" && it.operation == "UPSERT"
            }
        )
        val result = repo.details(task.id)!!
        assertEquals(2880, result.task.durationMinutes)
        assertTrue(result.task.isCompleted)
        assertEquals("✈️", result.list.icon)
        assertEquals(tag, result.tags.single().id)
        assertTrue(result.subtasks.single().isCompleted)
        assertNotNull(result.series)
        val restored = ImageStore(context).file(result.images.single().fileName)
        files.add(restored)
        assertNotEquals(file.name, restored.name)
        assertArrayEquals(file.readBytes(), restored.readBytes())
        assertEquals(123L, db.dao().receipt(task.id)!!.triggerAt)
        backups.restore(backups.prepare(bytes.inputStream()))
        db.dao().referencedImages().forEach { files.add(ImageStore(context).file(it)) }
        assertEquals(2, backups.prepare(archive().inputStream()).use { it.tasks })
    }

    @Test
    fun rejectsTraversalUnknownVersionAndInvalidRelationsWithoutChangingData() = runBlocking {
        val task = TaskEntity(title = "Keep")
        repo.create(task)
        val data = json(archive())
        rejected(zip(data.toString(), "../escape.image"))
        rejected(zip(JSONObject(data.toString()).put("version", 99).toString()))
        data.getJSONObject("tables").getJSONArray("tasks").getJSONObject(0).put("listId", "missing")
        rejected(zip(data.toString()))
        assertEquals("Keep", repo.details(task.id)!!.task.title)
    }

    @Test
    fun rejectsInvalidDurationsAndMissingImages() = runBlocking {
        repo.create(TaskEntity(title = "Keep"))
        val data = json(archive())
        data
            .getJSONObject("tables")
            .getJSONArray("tasks")
            .getJSONObject(0)
            .put("durationMinutes", 60)
        rejected(zip(data.toString()))
        val valid = json(archive())
        valid
            .getJSONObject("tables")
            .getJSONArray("task_images")
            .put(
                JSONObject()
                    .put("id", newId())
                    .put("taskId", "unused")
                    .put("fileName", "missing.image")
                    .put("createdAt", 0)
            )
        rejected(zip(valid.toString()))
    }

    @Test
    fun failedReplacementRollsBackExistingData() = runBlocking {
        val task = TaskEntity(title = "Current")
        repo.create(task)
        val prepared = backups.prepare(archive().inputStream())
        prepared.data
            .getJSONObject("tables")
            .getJSONArray("tasks")
            .getJSONObject(0)
            .put("priority", 99)
        try {
            backups.restore(prepared)
            fail("Must reject")
        } catch (_: IllegalArgumentException) {}
        assertEquals(0, repo.details(task.id)!!.task.priority)
        assertFalse(prepared.directory.exists())
    }
}
