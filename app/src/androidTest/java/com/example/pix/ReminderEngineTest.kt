package com.example.pix

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.ReminderRules
import com.example.pix.reminders.*
import java.time.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class ReminderEngineTest {
    private lateinit var db: PixDatabase
    private lateinit var repository: TaskRepository
    private lateinit var gateway: FakeGateway
    private lateinit var engine: ReminderEngine
    private val now = Instant.parse("2026-09-17T12:00:00Z")

    @Before
    fun setup() = runBlocking {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixDatabase::class.java,
                )
                .build()
        repository = TaskRepository(db)
        repository.initialize()
        gateway = FakeGateway()
        engine = ReminderEngine(db, gateway, Clock.fixed(now, ZoneOffset.UTC), { ZoneOffset.UTC })
    }

    @After fun close() = db.close()

    private fun task(minute: Int) =
        TaskEntity(
            title = "Reminder",
            dueDay = LocalDate.of(2026, 9, 17).toEpochDay(),
            minuteOfDay = minute,
        )

    @Test
    fun editingReschedulesAndCompletionDeletionOrAllDayCancel() = runBlocking {
        var t = task(780)
        repository.create(t)
        engine.reconcile()
        assertEquals(ReminderRules.trigger(t, ZoneOffset.UTC), gateway.scheduled[t.id])
        t = t.copy(minuteOfDay = 800)
        repository.edit(t, emptySet())
        engine.reconcile()
        assertEquals(ReminderRules.trigger(t, ZoneOffset.UTC), gateway.scheduled[t.id])
        repository.complete(t.id, true)
        engine.reconcile()
        assertTrue(t.id in gateway.cancelled)
        repository.complete(t.id, false)
        engine.reconcile()
        assertEquals(1, gateway.known.size)
        repository.edit(t.copy(minuteOfDay = null), emptySet())
        engine.reconcile()
        assertTrue(gateway.known.isEmpty())
        repository.edit(t, emptySet())
        engine.reconcile()
        repository.delete(t.id)
        engine.reconcile()
        assertTrue(gateway.known.isEmpty())
    }

    @Test
    fun alarmAndWorkerDeliverOnceAndStaleAlarmIsIgnored() = runBlocking {
        val t = task(719)
        repository.create(t)
        val trigger = ReminderRules.trigger(t, ZoneOffset.UTC)!!
        engine.deliver(t.id, trigger - 60000)
        assertEquals(0, gateway.shown)
        engine.deliver(t.id, trigger)
        engine.deliver(t.id, trigger)
        assertEquals(1, gateway.shown)
        assertEquals(trigger, db.dao().receipt(t.id)!!.triggerAt)
    }

    @Test
    fun overdueKnownReminderIsRecoveredAfterRebootButOldUnscheduledTasksAreNot() = runBlocking {
        val t = task(719)
        repository.create(t)
        engine.reconcile()
        assertEquals(0, gateway.shown)
        gateway.known = mapOf(t.id to ReminderRules.trigger(t, ZoneOffset.UTC)!!)
        engine.reconcile()
        engine.reconcile()
        assertEquals(1, gateway.shown)
    }

    @Test
    fun deniedNotificationsNeverMarkDelivered() = runBlocking {
        val t = task(719)
        repository.create(t)
        gateway.allowed = false
        engine.deliver(t.id, ReminderRules.trigger(t, ZoneOffset.UTC)!!)
        assertNull(db.dao().receipt(t.id))
        assertEquals(0, gateway.shown)
    }

    @Test
    fun staleActionsAreIgnoredAndSnoozeUpdatesDatabase() = runBlocking {
        val t = task(719)
        repository.create(t)
        val trigger = ReminderRules.trigger(t, ZoneOffset.UTC)!!
        engine.act(t.id, trigger - 60000, ACTION_COMPLETE, repository)
        assertFalse(db.dao().task(t.id)!!.isCompleted)
        engine.act(t.id, trigger, ACTION_SNOOZE, repository)
        assertEquals(780, db.dao().task(t.id)!!.minuteOfDay)
        engine.act(
            t.id,
            ReminderRules.trigger(db.dao().task(t.id)!!, ZoneOffset.UTC)!!,
            ACTION_COMPLETE,
            repository,
        )
        assertTrue(db.dao().task(t.id)!!.isCompleted)
    }


    @Test
    fun notificationActionsUseRepositoryAndTransactionalOutbox() = runBlocking {
        val t = task(719)
        repository.create(t)
        db.syncDao().clear()
        val trigger = ReminderRules.trigger(t, ZoneOffset.UTC)!!

        engine.act(t.id, trigger, ACTION_SNOOZE, repository)
        var queued = db.syncDao().pendingFor("tasks", t.id).single()
        assertEquals("UPSERT", queued.operation)
        assertEquals(780, org.json.JSONObject(queued.payload).getInt("minute_of_day"))

        db.syncDao().clear()
        val snoozed = db.dao().task(t.id)!!
        val snoozedTrigger = ReminderRules.trigger(snoozed, ZoneOffset.UTC)!!
        engine.act(t.id, snoozedTrigger, ACTION_COMPLETE, repository)
        queued = db.syncDao().pendingFor("tasks", t.id).single()
        assertEquals("UPSERT", queued.operation)
        assertTrue(org.json.JSONObject(queued.payload).getBoolean("is_completed"))
    }

    private class FakeGateway : ReminderGateway {
        var allowed = true
        var known = emptyMap<String, Long>()
        val scheduled = mutableMapOf<String, Long>()
        val cancelled = mutableSetOf<String>()
        var shown = 0

        override fun enabled() = true

        override fun notificationsAllowed() = allowed

        override fun knownSchedules() = known

        override fun rememberSchedules(schedules: Map<String, Long>) {
            known = schedules
        }

        override fun schedule(id: String, trigger: Long) {
            scheduled[id] = trigger
        }

        override fun cancel(id: String) {
            cancelled += id
            scheduled.remove(id)
        }

        override fun show(details: TaskWithDetails, trigger: Long): Boolean {
            if (!allowed) return false
            shown++
            return true
        }
    }
}
