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
    runCatching { TaskWidget().updateAll(context) }
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

    private val roomObserver =
        object : InvalidationTracker.Observer("tasks", "lists", "tags", "task_tags") {
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
        requestUpdate()
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
