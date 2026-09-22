package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Platform AppWidget receiver for the weekly calendar.
 *
 * We intentionally do not extend GlanceAppWidgetReceiver here: that receiver manages updates via a
 * WorkManager-backed Glance session, which is useful for most widgets but introduced multi-second
 * cold-start latency for day/week navigation. Rendering still uses Jetpack Glance through
 * CalendarWeekWidgetUpdater and GlanceAppWidget.compose().
 */
class CalendarWeekWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        renderAsync(context, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        renderAsync(context, intArrayOf(appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = CalendarWidgetConfigStore(context)
        appWidgetIds.forEach { appWidgetId ->
            store.delete(appWidgetId)
            CalendarWidgetDataSource.invalidate(appWidgetId)
        }
    }

    private fun renderAsync(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    CalendarWeekWidgetUpdater.update(appContext, appWidgetId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
