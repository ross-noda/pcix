package com.example.pix.reminders

import android.content.*
import android.util.Log
import androidx.work.*
import com.example.pix.PixApplication
import kotlinx.coroutines.*

class ReconcileWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            (applicationContext as PixApplication).reminders.reconcile()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PixReminders", "Reconciliation failed; retry scheduled")
            Result.retry()
        }
}

class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("taskId") ?: return Result.failure()
        return try {
            (applicationContext as PixApplication)
                .reminders
                .deliver(id, inputData.getLong("trigger", -1))
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PixReminders", "Delivery failed; retry scheduled")
            Result.retry()
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("taskId") ?: return
        val trigger = intent.getLongExtra("trigger", -1)
        val action = intent.action ?: return
        if (action == ACTION_COMPLETE || action == ACTION_SNOOZE) {
            // Persist the command before inline execution; expected deadline makes retries
            // idempotent.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "action-$id-$trigger-$action",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<ReminderActionWorker>()
                        .setInputData(
                            workDataOf("taskId" to id, "trigger" to trigger, "action" to action)
                        )
                        .build(),
                )
        }
        val pending = goAsync()
        val app = context.applicationContext as PixApplication
        app.backgroundScope.launch {
            try {
                withTimeout(8000) {
                    if (action == ACTION_FIRE) app.reminders.deliver(id, trigger)
                    else app.reminders.act(id, trigger, action, app.repository)
                }
            } catch (_: Exception) {
                Log.w("PixReminders", "Receiver interrupted; reconciliation scheduled")
                ReminderWork.reconcile(context)
            } finally {
                pending.finish()
            }
        }
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (
            intent.action in
                setOf(
                    Intent.ACTION_BOOT_COMPLETED,
                    Intent.ACTION_TIME_CHANGED,
                    Intent.ACTION_TIMEZONE_CHANGED,
                    Intent.ACTION_MY_PACKAGE_REPLACED,
                    android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
                )
        ) {
            ReminderWork.reconcile(context)
        }
    }
}

class ReminderActionWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("taskId") ?: return Result.failure()
        val action = inputData.getString("action") ?: return Result.failure()
        val app = applicationContext as PixApplication
        return try {
            app.reminders.act(id, inputData.getLong("trigger", -1), action, app.repository)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PixReminders", "Action failed; retry scheduled")
            Result.retry()
        }
    }
}
