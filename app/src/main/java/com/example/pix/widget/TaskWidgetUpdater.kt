package com.example.pix.widget

import android.content.Context
import android.content.SharedPreferences
import androidx.glance.appwidget.updateAll
import androidx.room.InvalidationTracker
import com.example.pix.data.PixDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun updateAllPixWidgets(context: Context) {
    CalendarWidgetDataSource.invalidateAll()
    runCatching { HabitWidgetUpdater.updateAll(context) }
    runCatching { TaskWidget().updateAll(context) }
    runCatching { updateSingleTaskWidgets(context) }
    runCatching { MonthWidgetUpdater.updateAll(context) }
    runCatching { MatrixWidget().updateAll(context) }
    runCatching { CalendarWeekWidgetUpdater.updateAll(context) }
}

class TaskWidgetUpdater(
    private val context: Context,
    private val database: PixDatabase,
    private val scope: CoroutineScope,
) {
    private var updateJob: Job? = null
    private var started = false
    private val appearance = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private val matrix = context.getSharedPreferences("matrix", Context.MODE_PRIVATE)
    private val matrixListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> requestUpdate() }

    private val roomObserver =
        object : InvalidationTracker.Observer("tasks", "lists", "tags", "task_tags", "google_calendars", "google_events", "habits", "habit_rules", "habit_logs", "habit_groups") {
            override fun onInvalidated(tables: Set<String>) = requestUpdate()
        }

    private val appearanceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme" || key == "accent" || key == "textSize" || key == "fontStyle") {
                requestUpdate()
            }
        }

    fun start() {
        if (started) return
        started = true
        database.invalidationTracker.addObserver(roomObserver)
        appearance.registerOnSharedPreferenceChangeListener(appearanceListener)
        matrix.registerOnSharedPreferenceChangeListener(matrixListener)
        requestUpdate()
        WidgetDayWorker.schedule(context)
    }

    fun requestUpdate() {
        updateJob?.cancel()
        updateJob =
            scope.launch {
                delay(150)
                updateAllPixWidgets(context)
            }
    }
}

/** One update at the next local day boundary; WorkManager survives process death and reboot. */
class WidgetDayWorker(context: Context, parameters: androidx.work.WorkerParameters) :
    androidx.work.CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        updateAllPixWidgets(applicationContext)
        schedule(applicationContext, androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
    companion object {
        fun schedule(context: Context, policy: androidx.work.ExistingWorkPolicy = androidx.work.ExistingWorkPolicy.KEEP) {
            val now = java.time.ZonedDateTime.now()
            val next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
            val delay = java.time.Duration.between(now, next).toMillis().coerceAtLeast(1000)
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("pix-widget-day", policy,
                androidx.work.OneTimeWorkRequestBuilder<WidgetDayWorker>()
                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS).build())
        }
    }
}
