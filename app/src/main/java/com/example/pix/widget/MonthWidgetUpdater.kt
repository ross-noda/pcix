package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Same direct RemoteViews path as the weekly widget; serialize state changes AND rendering. */
object MonthWidgetUpdater {
    private val locks = ConcurrentHashMap<Int, Mutex>()
    suspend fun update(context: Context, id: Int, delta: Int = 0) {
        require(delta in -1..1)
        val app = context.applicationContext
        locks.getOrPut(id) { Mutex() }.withLock {
            val manager = AppWidgetManager.getInstance(app)
            val provider = manager.getAppWidgetInfo(id)?.provider ?: return@withLock
            if (provider != ComponentName(app, CalendarMonthWidgetReceiver::class.java)) return@withLock
            if (delta != 0) {
                val prefs = app.getSharedPreferences("widget.month", 0)
                val offset = Math.addExact(prefs.getInt("offset:$id", 0), delta)
                check(prefs.edit().putInt("offset:$id", offset).commit())
            }
            val glanceId = GlanceAppWidgetManager(app).getGlanceIdBy(id)
            val views = CalendarMonthWidget().compose(context = app, id = glanceId, options = manager.getAppWidgetOptions(id))
            manager.updateAppWidget(id, views)
        }
    }
    suspend fun updateAll(context: Context) {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, CalendarMonthWidgetReceiver::class.java))
            .forEach { update(context, it) }
    }
}
