package com.example.pix.reminders

import android.app.*
import android.content.*
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.work.*
import com.example.pix.MainActivity
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.PixDatabase
import com.example.pix.domain.HabitRules
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Shares notification permission/channel and repair lifecycle with task reminders, with distinct IDs. */
class HabitReminders(private val context: Context, private val db: PixDatabase, private val gateway: ReminderScheduler) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("habit-reminders", Context.MODE_PRIVATE)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val work = WorkManager.getInstance(context)
    private fun pending(id: String, day: Long, trigger: Long) = PendingIntent.getBroadcast(context, 0,
        Intent(context, HabitReminderReceiver::class.java).setData("pix://habit-reminder/$id".toUri()).putExtra("id", id).putExtra("day", day).putExtra("trigger", trigger), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun reconcile() = mutex.withLock { reconcileLocked() }
    private suspend fun reconcileLocked(firingId: String? = null) {
        val now = ZonedDateTime.now(); val today = now.toLocalDate().toEpochDay()
        val known = JSONObject(prefs.getString("scheduled", "{}")!!)
        val desired = JSONObject()
        val rules = db.habitDao().rules().groupBy { it.habitId }
        if (gateway.enabled() && gateway.notificationsAllowed()) db.habitDao().habits().filter { it.active && it.reminderMinute != null }.forEach { h ->
            val minute = requireNotNull(h.reminderMinute)
            for (day in today..today + 3660) {
                val rule = HabitRules.at(rules[h.id].orEmpty(), day) ?: continue
                if (!HabitRules.scheduled(rule, day)) continue
                val time = LocalDate.ofEpochDay(day).atTime(minute / 60, minute % 60).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                if (time <= now.toInstant().toEpochMilli() && known.optLong(h.id, -1) != time) continue
                val log = db.habitDao().log(h.id, day)
                if (log?.skipped == true || HabitRules.complete(rule, log) || prefs.getLong("sent-${h.id}", Long.MIN_VALUE) >= day) continue
                desired.put(h.id, time)
                if (known.optLong(h.id, -1) != time) cancel(h.id, dismiss = false, cancelWork = h.id != firingId)
                run {
                    // Re-arm after reboot even when persisted scheduling metadata is unchanged.
                    val intent = pending(h.id, day, time)
                    try { if (gateway.exactAllowed()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent) }
                    catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, intent) }
                    work.enqueueUniqueWork("habit-reminder-${h.id}-$time", ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<HabitReminderWorker>().setInputData(workDataOf("id" to h.id, "day" to day, "trigger" to time))
                            .setInitialDelay((time - System.currentTimeMillis()).coerceAtLeast(0), TimeUnit.MILLISECONDS).addTag("habit-reminder-${h.id}").build())
                }
                break
            }
        }
        known.keys().asSequence().filter { !desired.has(it) }.toList().forEach { cancel(it, dismiss = it != firingId, cancelWork = it != firingId) }
        check(prefs.edit().putString("scheduled", desired.toString()).commit())
    }
    private fun cancel(id: String, dismiss: Boolean = true, cancelWork: Boolean = true) {
        alarms.cancel(pending(id, 0, 0))
        if (cancelWork) work.cancelAllWorkByTag("habit-reminder-$id")
        if (dismiss) notifications.cancel("habit-$id", 2)
    }
    suspend fun deliver(id: String, day: Long, trigger: Long) = mutex.withLock {
        val now = ZonedDateTime.now()
        val known = JSONObject(prefs.getString("scheduled", "{}")!!)
        if (known.optLong(id, -1) != trigger || System.currentTimeMillis() < trigger || now.toLocalDate().toEpochDay() != day) return@withLock
        val habit = db.habitDao().habit(id)
        val rule = HabitRules.at(db.habitDao().rules().filter { it.habitId == id }, day)
        val log = db.habitDao().log(id, day)
        if (habit?.active == true && habit.reminderMinute != null && rule != null && HabitRules.scheduled(rule, day) && !HabitRules.complete(rule, log) && log?.skipped != true && gateway.enabled() && gateway.notificationsAllowed() && prefs.getLong("sent-$id", Long.MIN_VALUE) < day) {
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).setData("pix://habit/$id".toUri()).putExtra("habitId", id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL).setSmallIcon(R.drawable.ic_reminder).setContentTitle(habit.name)
                .setContentText(context.getString(R.string.h_habits)).setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setCategory(NotificationCompat.CATEGORY_REMINDER).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
            try { notifications.notify("habit-$id", 2, notification); check(prefs.edit().putLong("sent-$id", day).commit()) } catch (_: SecurityException) { }
        }
        reconcileLocked(id)
    }
}
class HabitReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PixApplication).habitReminders.deliver(requireNotNull(inputData.getString("id")), inputData.getLong("day", -1), inputData.getLong("trigger", -1)); Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { Result.retry() }
}
class HabitReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val pending = goAsync(); val app = context.applicationContext as PixApplication
        app.backgroundScope.launch { try { app.habitReminders.deliver(id, intent.getLongExtra("day", -1), intent.getLongExtra("trigger", -1)) } finally { pending.finish() } }
    }
}
