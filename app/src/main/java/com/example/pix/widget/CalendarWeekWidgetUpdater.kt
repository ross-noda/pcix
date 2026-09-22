package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Direct renderer for the weekly calendar widget.
 *
 * Unlike GlanceAppWidget.update(), this path does not enqueue/wake a managed Glance WorkManager
 * session. It composes a one-shot RemoteViews snapshot and publishes it directly to AppWidgetManager.
 * Calendar navigation uses this path so taps feel like normal widget controls instead of delayed
 * background jobs.
 */
object CalendarWeekWidgetUpdater {
    private val locks = ConcurrentHashMap<Int, Mutex>()

    suspend fun update(context: Context, appWidgetId: Int) {
        val appContext = context.applicationContext
        lockFor(appWidgetId).withLock {
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            if (appWidgetManager.getAppWidgetInfo(appWidgetId) == null) return@withLock

            val glanceId = GlanceAppWidgetManager(appContext).getGlanceIdBy(appWidgetId)
            val remoteViews =
                CalendarWeekWidget().compose(
                    context = appContext,
                    id = glanceId,
                    options = appWidgetManager.getAppWidgetOptions(appWidgetId),
                )
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, CalendarWeekWidgetReceiver::class.java)
        appWidgetManager.getAppWidgetIds(component).forEach { appWidgetId ->
            update(appContext, appWidgetId)
        }
    }

    private fun lockFor(appWidgetId: Int): Mutex = locks.getOrPut(appWidgetId) { Mutex() }
}
