package com.example.pix

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val helper = MigrationTestHelper(instrumentation, PixDatabase::class.java)
    private val migrations =
        arrayOf(
            PixDatabase.MIGRATION_1_2,
            PixDatabase.MIGRATION_2_3,
            PixDatabase.MIGRATION_3_4,
            PixDatabase.MIGRATION_4_5,
            PixDatabase.MIGRATION_5_6,
            PixDatabase.MIGRATION_6_7,
            PixDatabase.MIGRATION_7_8,
        )

    @Test fun versionOneDataSurvivesMigration() = verifyMigration(1)

    @Test fun versionTwoDataSurvivesMigration() = verifyMigration(2)

    @Test fun versionThreeDataSurvivesMigration() = verifyMigration(3)

    @Test fun versionFourDataSurvivesMigration() = verifyMigration(4)

    @Test fun versionFiveDataSurvivesMigration() = verifyMigration(5)

    @Test
    fun versionSixOutboxPersistsAndUnownedGoogleCacheIsResetByV8() = runBlocking {
        val context = instrumentation.targetContext
        val name = "migration-v6-reopen-${System.nanoTime()}.db"
        try {
            helper.createDatabase(name, 6).use { db ->
                db.execSQL(
                    "INSERT INTO sync_outbox(id,entityType,entityId,operation,payload,createdAt,attemptCount,lastAttemptAt) VALUES('outbox-1','tasks','task-1','UPSERT','{}',100,2,200)"
                )
                db.execSQL(
                    "INSERT INTO sync_state(accountId,checkpoint,lastSuccessAt) VALUES('account-1','checkpoint-1',300)"
                )
                db.execSQL(
                    "INSERT INTO google_calendars(id,summary,colorArgb,timeZone,enabled,accessRole) VALUES('calendar-1','Legacy calendar',123,'Europe/Rome',1,'reader')"
                )
                db.execSQL(
                    "INSERT INTO google_events(id,calendarId,title,description,location,startDay,endDay,startMinute,endMinute,allDay,status,cancelled,updatedAt,recurringEventId,colorArgb) VALUES('event-1','calendar-1','Event','Description','Here',22000,22001,600,660,0,'confirmed',0,400,NULL,456)"
                )
                db.execSQL(
                    "INSERT INTO google_sync_state(calendarId,syncToken,lastSyncAt) VALUES('calendar-1','token-1',500)"
                )
            }

            val db =
                Room.databaseBuilder(context, PixDatabase::class.java, name)
                    .addMigrations(*migrations)
                    .build()
            try {
                val pending = db.syncDao().pending().single()
                assertEquals("outbox-1", pending.id)
                assertEquals("tasks", pending.entityType)
                assertEquals("task-1", pending.entityId)
                assertEquals("UPSERT", pending.operation)
                assertEquals("{}", pending.payload)
                assertEquals(100L, pending.createdAt)
                assertEquals(2, pending.attemptCount)
                assertEquals(200L, pending.lastAttemptAt)

                val syncState = db.syncDao().state("account-1")!!
                assertNull(syncState.checkpoint)
                assertEquals(300L, syncState.lastSuccessAt)
                assertEquals("Idle", syncState.status)
                assertTrue(db.syncDao().versions("account-1").isEmpty())

                // v7 Calendar rows had no explicit Google-account owner. v8 intentionally drops
                // only that cache instead of guessing ownership from calendarId.
                assertNull(db.googleDao().account())
                assertTrue(db.googleDao().calendars("missing").isEmpty())
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun verifyMigration(version: Int) {
        val name = "migration-v${version}-to-v8-${System.nanoTime()}.db"
        try {
            helper.createDatabase(name, version).use { db -> seedLegacyData(db, version) }

            helper.runMigrationsAndValidate(name, 8, true, *migrations).use { db ->
                assertCoreData(db, version)
                assertVersionSpecificData(db, version)
                assertVersionEightInfrastructure(db)
                assertNoForeignKeyViolations(db)
            }
        } finally {
            instrumentation.targetContext.deleteDatabase(name)
        }
    }

    private fun seedLegacyData(db: SupportSQLiteDatabase, version: Int) {
        val listColumns = if (version >= 4) "id,name,icon,color,sortOrder" else "id,name,color,sortOrder"
        val listValues =
            if (version >= 4) "'$LIST_ID','Legacy list','🎓',123,17"
            else "'$LIST_ID','Legacy list',123,17"
        db.execSQL("INSERT INTO lists($listColumns) VALUES($listValues)")
        db.execSQL(
            "INSERT INTO tags(id,name,normalizedName,color) VALUES('$TAG_ID','Legacy Tag','legacy tag',456)"
        )

        insertTask(
            db = db,
            version = version,
            id = TASK_ID,
            title = "Keep me",
            notes = "Notes survive",
            seriesId = null,
            originalDay = null,
            isTemplate = false,
        )
        db.execSQL("INSERT INTO task_tags(taskId,tagId) VALUES('$TASK_ID','$TAG_ID')")
        db.execSQL(
            "INSERT INTO subtasks(id,taskId,title,isCompleted,sortOrder,createdAt,updatedAt) VALUES('$SUBTASK_ID','$TASK_ID','Legacy subtask',1,31,333,444)"
        )

        if (version >= 2) {
            db.execSQL(
                "INSERT INTO reminder_receipts(taskId,triggerAt) VALUES('$TASK_ID',777)"
            )
        }

        if (version >= 3) {
            insertTask(
                db = db,
                version = version,
                id = TEMPLATE_ID,
                title = "Recurring template",
                notes = "Template notes",
                seriesId = SERIES_ID,
                originalDay = 21000,
                isTemplate = true,
            )
            insertTask(
                db = db,
                version = version,
                id = OCCURRENCE_ID,
                title = "Recurring occurrence",
                notes = "Occurrence notes",
                seriesId = SERIES_ID,
                originalDay = 21001,
                isTemplate = false,
            )
            db.execSQL(
                "INSERT INTO recurring_series(id,rule,anchorDay,templateTaskId,endBefore) VALUES('$SERIES_ID','FREQ=WEEKLY;BYDAY=MO,WE',21000,'$TEMPLATE_ID',22000)"
            )
        }

        if (version >= 4) {
            db.execSQL(
                "INSERT INTO task_images(id,taskId,fileName,createdAt) VALUES('$IMAGE_ID','$TASK_ID','legacy-image.jpg',888)"
            )
        }
    }

    private fun insertTask(
        db: SupportSQLiteDatabase,
        version: Int,
        id: String,
        title: String,
        notes: String,
        seriesId: String?,
        originalDay: Long?,
        isTemplate: Boolean,
    ) {
        val columns =
            mutableListOf(
                "id",
                "title",
                "notes",
                "listId",
                "dueDay",
                "minuteOfDay",
                "priority",
                "isCompleted",
                "completedAt",
            )
        val values =
            mutableListOf(
                sqlText(id),
                sqlText(title),
                sqlText(notes),
                sqlText(LIST_ID),
                "21000",
                "555",
                "3",
                "0",
                "NULL",
            )

        if (version >= 3) {
            columns += listOf("seriesId", "originalDay", "isTemplate", "isSkipped")
            values +=
                listOf(
                    seriesId?.let(::sqlText) ?: "NULL",
                    originalDay?.toString() ?: "NULL",
                    if (isTemplate) "1" else "0",
                    "0",
                )
        }
        if (version >= 4) {
            columns += listOf("matrixUrgent", "matrixImportant")
            values += listOf("1", "0")
        }
        if (version >= 5) {
            columns += "durationMinutes"
            values += "90"
        }

        columns += listOf("sortOrder", "createdAt", "updatedAt")
        values += listOf("29", "111", "222")
        db.execSQL("INSERT INTO tasks(${columns.joinToString()}) VALUES(${values.joinToString()})")
    }

    private fun assertCoreData(db: SupportSQLiteDatabase, version: Int) {
        db.query("SELECT * FROM lists WHERE id='$LIST_ID'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy list", cursor.text("name"))
            assertEquals(123, cursor.int("color"))
            assertEquals(17L, cursor.long("sortOrder"))
            assertEquals(if (version >= 4) "🎓" else "📋", cursor.text("icon"))
            assertEquals(0L, cursor.long("createdAt"))
            assertEquals(0L, cursor.long("updatedAt"))
        }

        db.query("SELECT * FROM tags WHERE id='$TAG_ID'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Tag", cursor.text("name"))
            assertEquals("legacy tag", cursor.text("normalizedName"))
            assertEquals(456, cursor.int("color"))
            assertEquals(0L, cursor.long("createdAt"))
            assertEquals(0L, cursor.long("updatedAt"))
        }

        db.query("SELECT * FROM tasks WHERE id='$TASK_ID'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Keep me", cursor.text("title"))
            assertEquals("Notes survive", cursor.text("notes"))
            assertEquals(LIST_ID, cursor.text("listId"))
            assertEquals(21000L, cursor.long("dueDay"))
            assertEquals(555, cursor.int("minuteOfDay"))
            assertEquals(3, cursor.int("priority"))
            assertEquals(29L, cursor.long("sortOrder"))
            assertEquals(111L, cursor.long("createdAt"))
            assertEquals(222L, cursor.long("updatedAt"))
            assertEquals(0, cursor.int("isCompleted"))
            assertEquals(0, cursor.int("isTemplate"))
            assertEquals(0, cursor.int("isSkipped"))
        }

        assertEquals(
            1L,
            db.scalarLong(
                "SELECT COUNT(*) FROM task_tags WHERE taskId='$TASK_ID' AND tagId='$TAG_ID'"
            ),
        )
        db.query("SELECT * FROM subtasks WHERE id='$SUBTASK_ID'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(TASK_ID, cursor.text("taskId"))
            assertEquals("Legacy subtask", cursor.text("title"))
            assertEquals(1, cursor.int("isCompleted"))
            assertEquals(31L, cursor.long("sortOrder"))
            assertEquals(333L, cursor.long("createdAt"))
            assertEquals(444L, cursor.long("updatedAt"))
        }
    }

    private fun assertVersionSpecificData(db: SupportSQLiteDatabase, version: Int) {
        if (version >= 2) {
            assertEquals(
                777L,
                db.scalarLong(
                    "SELECT triggerAt FROM reminder_receipts WHERE taskId='$TASK_ID'"
                ),
            )
        } else {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM reminder_receipts"))
        }

        if (version >= 3) {
            db.query("SELECT * FROM recurring_series WHERE id='$SERIES_ID'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FREQ=WEEKLY;BYDAY=MO,WE", cursor.text("rule"))
                assertEquals(21000L, cursor.long("anchorDay"))
                assertEquals(TEMPLATE_ID, cursor.text("templateTaskId"))
                assertEquals(22000L, cursor.long("endBefore"))
                assertEquals(0L, cursor.long("updatedAt"))
            }
            db.query("SELECT * FROM tasks WHERE id='$TEMPLATE_ID'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(SERIES_ID, cursor.text("seriesId"))
                assertEquals(21000L, cursor.long("originalDay"))
                assertEquals(1, cursor.int("isTemplate"))
            }
            db.query("SELECT * FROM tasks WHERE id='$OCCURRENCE_ID'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(SERIES_ID, cursor.text("seriesId"))
                assertEquals(21001L, cursor.long("originalDay"))
                assertEquals(0, cursor.int("isTemplate"))
            }
        } else {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM recurring_series"))
        }

        db.query("SELECT matrixUrgent,matrixImportant,durationMinutes FROM tasks WHERE id='$TASK_ID'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                if (version >= 4) {
                    assertEquals(1, cursor.int("matrixUrgent"))
                    assertEquals(0, cursor.int("matrixImportant"))
                } else {
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("matrixUrgent")))
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("matrixImportant")))
                }
                if (version >= 5) {
                    assertEquals(90, cursor.int("durationMinutes"))
                } else {
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("durationMinutes")))
                }
            }

        if (version >= 4) {
            db.query("SELECT * FROM task_images WHERE id='$IMAGE_ID'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(TASK_ID, cursor.text("taskId"))
                assertEquals("legacy-image.jpg", cursor.text("fileName"))
                assertEquals(888L, cursor.long("createdAt"))
            }
        } else {
            assertEquals(0L, db.scalarLong("SELECT COUNT(*) FROM task_images"))
        }
    }

    private fun assertVersionEightInfrastructure(db: SupportSQLiteDatabase) {
        val expectedTables =
            setOf(
                "sync_outbox",
                "sync_state",
                "sync_entity_versions",
                "google_calendar_accounts",
                "google_calendars",
                "google_events",
                "google_sync_state",
            )
        val actual = mutableSetOf<String>()
        db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('sync_outbox','sync_state','sync_entity_versions','google_calendar_accounts','google_calendars','google_events','google_sync_state')"
            )
            .use { cursor ->
                while (cursor.moveToNext()) actual += cursor.getString(0)
            }
        assertEquals(expectedTables, actual)

        db.execSQL(
            "INSERT INTO sync_outbox(id,entityType,entityId,operation,payload,createdAt,attemptCount,lastAttemptAt) VALUES('migration-outbox','tasks','$TASK_ID','UPSERT','{}',1,0,NULL)"
        )
        db.execSQL(
            "INSERT INTO sync_state(accountId,checkpoint,lastSuccessAt,status) VALUES('migration-account','7',2,'Offline')"
        )
        db.execSQL(
            "INSERT INTO sync_entity_versions(accountId,entityType,entityId,serverVersion,deleted) VALUES('migration-account','tasks','$TASK_ID',7,0)"
        )
        assertEquals(
            1L,
            db.scalarLong("SELECT COUNT(*) FROM sync_outbox WHERE id='migration-outbox'"),
        )
        assertEquals(
            1L,
            db.scalarLong("SELECT COUNT(*) FROM sync_state WHERE accountId='migration-account' AND checkpoint='7' AND status='Offline'"),
        )
        assertEquals(
            1L,
            db.scalarLong("SELECT COUNT(*) FROM sync_entity_versions WHERE accountId='migration-account' AND serverVersion=7"),
        )
    }

    private fun assertNoForeignKeyViolations(db: SupportSQLiteDatabase) {
        db.query("PRAGMA foreign_key_check").use { cursor ->
            assertFalse("Foreign key violation after migration", cursor.moveToFirst())
        }
    }

    private fun SupportSQLiteDatabase.scalarLong(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue("Expected one row for: $sql", cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun android.database.Cursor.text(column: String): String =
        getString(getColumnIndexOrThrow(column))

    private fun android.database.Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))

    private fun android.database.Cursor.long(column: String): Long =
        getLong(getColumnIndexOrThrow(column))

    private fun sqlText(value: String): String = "'${value.replace("'", "''")}'"

    companion object {
        private const val LIST_ID = "list-legacy"
        private const val TAG_ID = "tag-legacy"
        private const val TASK_ID = "task-legacy"
        private const val SUBTASK_ID = "subtask-legacy"
        private const val SERIES_ID = "series-legacy"
        private const val TEMPLATE_ID = "template-legacy"
        private const val OCCURRENCE_ID = "occurrence-legacy"
        private const val IMAGE_ID = "image-legacy"
    }
}
