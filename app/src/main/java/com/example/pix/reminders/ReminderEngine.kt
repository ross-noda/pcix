package com.example.pix.reminders

import com.example.pix.data.*
import com.example.pix.domain.ReminderRules
import java.time.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared per-application engine serializes duplicate deliveries from alarm and backup worker. */
class ReminderEngine(
    private val db: PixDatabase,
    private val gateway: ReminderGateway,
    private val clock: Clock = Clock.systemUTC(),
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private val mutex = Mutex()
    private val dao
        get() = db.dao()

    suspend fun reconcile() =
        mutex.withLock {
            val tasks =
                if (gateway.enabled() && gateway.notificationsAllowed()) dao.timedTasks()
                else emptyList()
            val now = clock.millis()
            val known = gateway.knownSchedules()
            val schedules =
                tasks
                    .mapNotNull { task ->
                        ReminderRules.trigger(task, zone())?.let { task.id to it }
                    }
                    .filter { (id, trigger) -> trigger > now || known[id] == trigger }
                    .toMap()
            (known.keys - schedules.keys).forEach { gateway.cancel(it) }
            gateway.rememberSchedules(schedules)
            schedules.forEach { (id, trigger) ->
                if (known[id] != null && known[id] != trigger) gateway.cancel(id)
                if (trigger > now) gateway.schedule(id, trigger) else deliverLocked(id, trigger)
            }
        }

    suspend fun deliver(id: String, expectedTrigger: Long) =
        mutex.withLock { deliverLocked(id, expectedTrigger) }

    private suspend fun deliverLocked(id: String, expectedTrigger: Long) {
        val details = dao.details(id) ?: return
        val trigger = ReminderRules.trigger(details.task, zone()) ?: return
        if (trigger != expectedTrigger || trigger > clock.millis()) return
        if (dao.receipt(id)?.triggerAt == trigger) return
        if (gateway.show(details, trigger)) dao.saveReceipt(ReminderReceipt(id, trigger))
    }

    suspend fun act(id: String, expectedTrigger: Long, action: String, repository: TaskRepository) =
        mutex.withLock {
            val task = dao.task(id) ?: return@withLock
            // A stale notification must not complete or postpone a subsequently edited task.
            if (ReminderRules.trigger(task, zone()) != expectedTrigger) return@withLock
            when (action) {
                ACTION_COMPLETE -> repository.complete(id, true)
                ACTION_SNOOZE -> repository.snooze(id, clock.instant(), zone())
                else -> return@withLock
            }
            gateway.cancel(id)
        }
}
