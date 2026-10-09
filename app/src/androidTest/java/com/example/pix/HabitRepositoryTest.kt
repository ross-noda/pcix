package com.example.pix

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate

class HabitRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun rapidTapsAreAtomicReactiveAndOutboxed() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            val repo = HabitRepository(db); val day = LocalDate.now().toEpochDay()
            val group = HabitGroupEntity(name = "Health"); repo.group(group)
            val h = HabitEntity(name = "Water", groupId = group.id)
            repo.save(h, HabitRuleEntity(habitId = h.id, effectiveDay = day, quantity = true, target = 8))
            coroutineScope { repeat(8) { launch(Dispatchers.IO) { repo.record(h.id, day) } } }
            assertEquals(8, withTimeout(3000) { repo.day(day).first { it.singleOrNull()?.count == 8 } }.single().count)
            repo.record(h.id, day, delta = -1)
            assertEquals(7, db.habitDao().log(h.id, day)!!.count)
            assertEquals(1, db.syncDao().pending().count { it.entityType == "habit_logs" })
            repo.deleteGroup(group.id)
            assertNull(db.habitDao().habit(h.id)!!.groupId)
            assertEquals(7, db.habitDao().log(h.id, day)!!.count)
            assertTrue(runCatching { repo.record(h.id, day+1) }.isFailure)
            repo.update(h.copy(active = false, groupId = null))
            assertTrue(runCatching { repo.record(h.id, day) }.isFailure)
        } finally { db.close() }
    }
    @Test fun backupRoundTripAndRuleHistory() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            TaskRepository(db).initialize()
            val repo = HabitRepository(db); val day = LocalDate.now().toEpochDay()
            val g = HabitGroupEntity(name = "Health"); repo.group(g)
            val h = HabitEntity(name = "Brush", groupId = g.id, reminderMinute = 480)
            repo.save(h, HabitRuleEntity(habitId = h.id, effectiveDay = day-7), today = day-7)
            repo.record(h.id, day-1, count = 1)
            repo.save(h, HabitRuleEntity(habitId = h.id, effectiveDay = day, startDay = day-7, weekdays = 5), today = day)
            assertEquals(2, db.habitDao().rules().size)
            val backups = BackupRepository(context, db)
            val bytes = ByteArrayOutputStream().also { backups.export(it) }.toByteArray()
            val expected = db.habitDao().logs()
            repo.delete(h.id)
            backups.prepare(bytes.inputStream()).use { backups.restore(it) }
            assertEquals(expected, db.habitDao().logs())
            assertEquals(g.id, db.habitDao().groups().single().id)
            assertEquals(g.name, db.habitDao().groups().single().name)
            assertEquals(480, db.habitDao().habit(h.id)!!.reminderMinute)
            assertEquals(127, HabitRules.at(db.habitDao().rules(),day-1)!!.weekdays)
        } finally { db.close() }
    }
    @Test fun editingFutureHabitDoesNotRestoreAnObsoleteRule() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        try {
            val repo = HabitRepository(db); val today = LocalDate.now().toEpochDay()
            val h = HabitEntity(name = "Future")
            repo.save(h, HabitRuleEntity(habitId = h.id, effectiveDay = today+7))
            repo.save(h, HabitRuleEntity(habitId = h.id, effectiveDay = today+7, quantity = true, target = 8))
            assertEquals(8, HabitRules.at(db.habitDao().rules(), today+8)!!.target)
            assertEquals(1, db.habitDao().rules().size)
        } finally { db.close() }
    }
    @Test fun migrationTenToElevenPreservesExistingRows() {
        val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), PixDatabase::class.java)
        val name = "habits-migration-${System.nanoTime()}"
        try {
            helper.createDatabase(name,10).use { sql ->
                sql.execSQL("INSERT INTO lists(id,name,color,sortOrder,icon,createdAt,updatedAt) VALUES('l','Preserved',0,0,'REPEAT',1,1)")
                sql.execSQL("INSERT INTO sync_outbox(id,entityType,entityId,operation,payload,createdAt,attemptCount,lastAttemptAt) VALUES('o','lists','l','UPSERT','{}',1,2,NULL)")
            }
            helper.runMigrationsAndValidate(name,11,true,PixDatabase.MIGRATION_10_11).use { sql ->
                sql.query("SELECT name FROM lists WHERE id='l'").use { assertTrue(it.moveToFirst()); assertEquals("Preserved",it.getString(0)) }
                sql.query("SELECT attemptCount FROM sync_outbox WHERE id='o'").use { assertTrue(it.moveToFirst()); assertEquals(2,it.getInt(0)) }
                sql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            }
        } finally { context.deleteDatabase(name) }
    }
}
