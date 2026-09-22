package com.example.pix.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.*
import com.example.pix.MainActivity
import com.example.pix.R
import com.example.pix.data.TaskWithDetails
import java.util.concurrent.TimeUnit

const val REMINDER_CHANNEL = "task_reminders"
const val ACTION_FIRE = "com.example.pix.REMIND"
const val ACTION_COMPLETE = "com.example.pix.COMPLETE"
const val ACTION_SNOOZE = "com.example.pix.SNOOZE"

/** Narrow platform boundary: engine tests can verify scheduling without waiting for wall time. */
interface ReminderGateway {
    fun notificationsAllowed(): Boolean

    fun enabled(): Boolean

    fun knownSchedules(): Map<String, Long>

    fun rememberSchedules(schedules: Map<String, Long>)

    fun schedule(id: String, trigger: Long)

    fun cancel(id: String)

    fun show(details: TaskWithDetails, trigger: Long): Boolean
}

class ReminderScheduler(private val context: Context) : ReminderGateway {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val preferences = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
    private val work
        get() = WorkManager.getInstance(context)

    init {
        notifications.createNotificationChannel(
            NotificationChannel(
                    REMINDER_CHANNEL,
                    context.getString(R.string.reminder_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                )
                .apply {
                    setSound(
                        android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    enableVibration(true)
                }
        )
    }

    override fun notificationsAllowed(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            notifications.getNotificationChannel(REMINDER_CHANNEL)?.importance !=
                NotificationManager.IMPORTANCE_NONE

    fun soundEnabled(): Boolean {
        val channel = notifications.getNotificationChannel(REMINDER_CHANNEL)
        return channel != null &&
            channel.sound != null &&
            channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
    }

    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    override fun enabled(): Boolean = preferences.getBoolean("enabled", true)

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean("enabled", enabled) }
        ReminderWork.reconcile(context)
    }

    override fun knownSchedules(): Map<String, Long> {
        val json = org.json.JSONObject(preferences.getString("scheduled_v2", "{}").orEmpty())
        return json.keys().asSequence().associateWith { json.getLong(it) }
    }

    @SuppressLint(
        "UseKtx"
    ) // commit result is checked: scheduling metadata must reach disk before alarms.
    override fun rememberSchedules(schedules: Map<String, Long>) {
        check(
            preferences
                .edit()
                .putString("scheduled_v2", org.json.JSONObject(schedules).toString())
                .commit()
        )
    }

    fun intent(id: String, action: String, trigger: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java)
                .setAction(action)
                .setData(
                    Uri.Builder()
                        .scheme("pix")
                        .authority("reminders")
                        .appendPath(id)
                        .appendPath(action)
                        .build()
                )
                .putExtra("taskId", id)
                .putExtra("trigger", trigger),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun schedule(id: String, trigger: Long) {
        val pending = intent(id, ACTION_FIRE, trigger)
        if (exactAllowed()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } catch (_: SecurityException) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
        // Persists across reboot and survives revocation of exact-alarm access. May be delayed by
        // OS.
        val request =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf("taskId" to id, "trigger" to trigger))
                .setInitialDelay(
                    (trigger - System.currentTimeMillis()).coerceAtLeast(0),
                    TimeUnit.MILLISECONDS,
                )
                .addTag("reminder-$id")
                .build()
        work.enqueueUniqueWork("reminder-$id-$trigger", ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(id: String) {
        alarms.cancel(intent(id, ACTION_FIRE, 0))
        work.cancelAllWorkByTag("reminder-$id")
        notifications.cancel(id, 1)
    }

    override fun show(details: TaskWithDetails, trigger: Long): Boolean {
        if (!notificationsAllowed() || !enabled()) return false
        val id = details.task.id
        val open =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .setData("pix://task/$id".toUri())
                    .putExtra("taskId", id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat.Builder(context, REMINDER_CHANNEL)
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle(details.task.title)
                .setContentText(details.list.name)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(
                    R.drawable.ic_reminder,
                    context.getString(R.string.notification_complete),
                    intent(id, ACTION_COMPLETE, trigger),
                )
                .addAction(
                    R.drawable.ic_reminder,
                    context.getString(R.string.snooze_hour),
                    intent(id, ACTION_SNOOZE, trigger),
                )
                .build()
        return try {
            notifications.notify(id, 1, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}

object ReminderWork {
    fun reconcile(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "reminder-reconcile",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<ReconcileWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                    .build(),
            )
    }

    fun initialize(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "reminder-repair",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ReconcileWorker>(15, TimeUnit.MINUTES).build(),
            )
        reconcile(context)
    }
}
