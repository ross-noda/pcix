package com.example.pix

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.TaskEntity
import com.example.pix.domain.ReminderRules
import com.example.pix.reminders.*
import java.io.FileInputStream
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NotificationPlatformTest {
    @Test
    fun actualNotificationActionCompletesPersistedTask() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PixApplication
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation
                .executeShellCommand(
                    "pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
                )
                .use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        }
        app.scheduler.setEnabled(true)
        val due = ZonedDateTime.now().minusMinutes(1)
        val task =
            TaskEntity(
                title = "Platform reminder test",
                dueDay = due.toLocalDate().toEpochDay(),
                minuteOfDay = due.hour * 60 + due.minute,
            )
        try {
            app.repository.create(task)
            val trigger = ReminderRules.trigger(task, ZoneId.systemDefault())!!
            app.reminders.deliver(task.id, trigger)
            val manager = app.getSystemService(NotificationManager::class.java)
            val channel = manager.getNotificationChannel(REMINDER_CHANNEL)
            assertNotNull(channel.sound)
            assertTrue(channel.importance >= NotificationManager.IMPORTANCE_DEFAULT)
            assertEquals(
                android.media.AudioAttributes.USAGE_NOTIFICATION,
                channel.audioAttributes.usage,
            )
            assertTrue(app.scheduler.soundEnabled())
            withTimeout(5000) {
                while (manager.activeNotifications.none { it.tag == task.id }) delay(50)
            }
            assertEquals(
                2,
                manager.activeNotifications.first { it.tag == task.id }.notification.actions.size,
            )
            // Exercise the actual PendingIntent → BroadcastReceiver → repository path.
            manager.activeNotifications
                .first { it.tag == task.id }
                .notification
                .actions[0]
                .actionIntent
                .send()
            withTimeout(5000) {
                while (app.repository.details(task.id)?.task?.isCompleted != true) delay(50)
            }
            assertTrue(app.repository.details(task.id)!!.task.isCompleted)
            withTimeout(5000) {
                while (manager.activeNotifications.any { it.tag == task.id }) delay(50)
            }
        } finally {
            app.repository.delete(task.id)
            app.scheduler.cancel(task.id)
        }
    }

    @Test
    fun snoozeAndEditRescheduleTheRealPlatformDeadline() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PixApplication
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation
                .executeShellCommand(
                    "pm grant ${app.packageName} ${Manifest.permission.POST_NOTIFICATIONS}"
                )
                .use { FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
        }
        app.scheduler.setEnabled(true)
        val due = ZonedDateTime.now().minusMinutes(1)
        val task =
            TaskEntity(
                title = "Snooze platform test",
                dueDay = due.toLocalDate().toEpochDay(),
                minuteOfDay = due.hour * 60 + due.minute,
            )
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            app.repository.create(task)
            app.reminders.deliver(task.id, ReminderRules.trigger(task, ZoneId.systemDefault())!!)
            withTimeout(5000) {
                while (manager.activeNotifications.none { it.tag == task.id }) delay(50)
            }
            manager.activeNotifications
                .first { it.tag == task.id }
                .notification
                .actions[1]
                .actionIntent
                .send()
            withTimeout(5000) {
                while (
                    app.repository.details(task.id)!!.task.minuteOfDay == task.minuteOfDay
                ) delay(50)
            }
            val snoozed = app.repository.details(task.id)!!.task
            val expected = ReminderRules.trigger(snoozed, ZoneId.systemDefault())!!
            assertTrue(expected > System.currentTimeMillis())
            withTimeout(10000) {
                while (app.scheduler.knownSchedules()[task.id] != expected) delay(50)
            }
            val changed = snoozed.copy(dueDay = snoozed.dueDay!! + 1)
            app.repository.edit(changed, emptySet())
            val changedTrigger = ReminderRules.trigger(changed, ZoneId.systemDefault())!!
            withTimeout(10000) {
                while (app.scheduler.knownSchedules()[task.id] != changedTrigger) delay(50)
            }
            app.repository.delete(task.id)
            withTimeout(10000) { while (task.id in app.scheduler.knownSchedules()) delay(50) }
        } finally {
            app.repository.delete(task.id)
            app.scheduler.cancel(task.id)
        }
    }
}
