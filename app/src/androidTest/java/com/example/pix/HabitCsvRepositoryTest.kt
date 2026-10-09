package com.example.pix

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import java.io.StringWriter
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class HabitCsvRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val header = "Habit;Date;Total log;Unit;Status;Habit ID\n"
    @Test fun suppliedCsvImportsAndExportsEveryRecordOnAndroid() = runBlocking {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        org.junit.Assume.assumeTrue(assets.list("").orEmpty().contains("habit-import-fixture.csv"))
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            val repo = HabitRepository(db)
            val preview = assets.open("habit-import-fixture.csv").use { stream ->
                java.io.InputStreamReader(stream, Charsets.UTF_8.newDecoder()).use { repo.prepareCsv(it) }
            }
            assertEquals(17, preview.document.habits.size)
            assertEquals(2201, preview.document.logCount)
            val result = repo.importCsv(preview.document, false)
            assertEquals(17, result.newHabits); assertEquals(2201, result.writtenLogs)
            val out = StringWriter(); repo.exportCsv(out)
            val restored = HabitCsv.read(StringReader(out.toString()))
            assertEquals(preview.document.habits.associateBy { it.sourceId }, restored.habits.associateBy { it.sourceId })
            val again = repo.importCsv(restored, false)
            assertEquals(0, again.newHabits); assertEquals(2201, again.keptLogs)
        } finally { db.close() }
    }
    @Test fun importMergeOutboxAndBackupPreserveOriginalStates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            TaskRepository(db).initialize()
            val repo = HabitRepository(db)
            val date = LocalDate.now().minusDays(1)
            val doc = repo.prepareCsv(StringReader(header + "Water;$date;4;rep;Failed;foreign-id")).document
            val first = repo.importCsv(doc, false)
            assertEquals(1, first.newHabits); assertEquals(1, first.writtenLogs)
            assertEquals("Failed", db.habitDao().logs().single().sourceStatus)
            assertEquals("foreign-id", db.habitDao().habits().single().csvId)
            val repeated = repo.importCsv(doc, false)
            assertEquals(0, repeated.newHabits); assertEquals(1, repeated.keptLogs)
            assertEquals(1, db.habitDao().logs().size)
            assertEquals(1, db.syncDao().pending().count { it.entityType == "habit_logs" })
            val revised = repo.prepareCsv(StringReader(header + "Water;$date;2;rep;;foreign-id")).document
            repo.importCsv(revised, true)
            assertEquals("", db.habitDao().logs().single().sourceStatus)
            assertEquals(2, db.habitDao().logs().single().count)
            val output = StringWriter(); repo.exportCsv(output)
            assertEquals(revised, HabitCsv.read(StringReader(output.toString())))
            val backups = BackupRepository(context, db)
            val archive = ByteArrayOutputStream().also { backups.export(it) }.toByteArray()
            val original = db.habitDao().logs().single()
            backups.prepare(archive.inputStream()).use { backups.restore(it) }
            assertEquals(original, db.habitDao().logs().single())
            assertEquals("foreign-id", db.habitDao().habits().single().csvId)
            repo.record(doc.habits.single().id, date.toEpochDay(), count = 0)
            assertEquals("Failed", db.habitDao().logs().single().sourceStatus)
        } finally { db.close() }
    }
    @Test fun futureCsvDatesArePreviewedAndImportedWithoutChangingThem() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            val repo = HabitRepository(db)
            val future = LocalDate.now().plusYears(1)
            val preview = repo.prepareCsv(StringReader(header + "Water;$future;1;rep;Completed;foreign-id"))
            assertEquals(1, preview.futureLogs)
            assertEquals(1, repo.importCsv(preview.document, false).writtenLogs)
            assertEquals(future.toEpochDay(), db.habitDao().logs().single().day)
        } finally { db.close() }
    }
    @Test fun widgetCompletionIsIdempotentForChecksAndAtomicForCounters() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            val repo = HabitRepository(db)
            val day = LocalDate.now().toEpochDay()
            for (quantity in listOf(false, true)) {
                val h = HabitEntity(name = "Widget")
                repo.save(h, HabitRuleEntity(habitId = h.id, effectiveDay = day, quantity = quantity, step = if(quantity) 2 else 1))
                repeat(2) { repo.record(h.id, day, completeOnly = true) }
                assertEquals(if(quantity) 4 else 1, db.habitDao().log(h.id, day)?.count)
                assertTrue(runCatching { repo.record(h.id, day, completeOnly = true, guard = { false }) }.isFailure)
                assertEquals(if(quantity) 4 else 1, db.habitDao().log(h.id, day)?.count)
            }
        } finally { db.close() }
    }
    @Test fun failedImportRollsBackAllRowsAndOutbox() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            val repo = HabitRepository(db)
            val day = LocalDate.now().toEpochDay()
            val doc = HabitCsv.Document(listOf(
                HabitCsv.Habit("valid", "Valid", "rep", listOf(HabitCsv.Entry(day, 1, "Completed"))),
                HabitCsv.Habit("invalid", "Invalid", "rep", listOf(HabitCsv.Entry(day, -1, "Completed"))),
            ))
            assertTrue(runCatching { repo.importCsv(doc, false) }.isFailure)
            assertTrue(db.habitDao().habits().isEmpty()); assertTrue(db.habitDao().logs().isEmpty())
            assertTrue(db.syncDao().pending().isEmpty())
        } finally { db.close() }
    }
    @Test fun migrationElevenToTwelveKeepsHistoryAndDefaults() {
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PixDatabase::class.java)
        val name = "habit-csv-${System.nanoTime()}"
        try {
            helper.createDatabase(name, 11).use { sql ->
                sql.execSQL("INSERT INTO habits VALUES('h','Water','REPEAT',0,NULL,'',1,0,NULL,1,1)")
                sql.execSQL("INSERT INTO habit_logs VALUES('l','h',20000,4,0,1,1)")
            }
            helper.runMigrationsAndValidate(name,12,true,PixDatabase.MIGRATION_11_12).use { sql ->
                sql.query("SELECT unit,csvId FROM habits").use { assertTrue(it.moveToFirst()); assertEquals("rep",it.getString(0)); assertTrue(it.isNull(1)) }
                sql.query("SELECT count,sourceStatus FROM habit_logs").use { assertTrue(it.moveToFirst()); assertEquals(4,it.getInt(0)); assertTrue(it.isNull(1)) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
