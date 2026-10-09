package com.example.pix.widget

import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import com.example.pix.PixApplication
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object HabitWidgetUpdater {
    private val lock = Mutex()
    suspend fun update(context: Context, id: Int) = lock.withLock {
        val manager = AppWidgetManager.getInstance(context)
        if (manager.getAppWidgetInfo(id)?.provider != ComponentName(context, HabitWidgetReceiver::class.java)) return@withLock
        val views = HabitWidget().compose(context, id = GlanceAppWidgetManager(context).getGlanceIdBy(id), options = manager.getAppWidgetOptions(id))
        manager.updateAppWidget(id, views)
    }
    suspend fun updateAll(context: Context) {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, HabitWidgetReceiver::class.java)).forEach { update(context, it) }
    }
}

class HabitWidgetReceiver : AppWidgetProvider() {
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetBackgroundStore(context).delete(it) } }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, intArrayOf(id))
    private fun render(context: Context, ids: IntArray) {
        val pending = goAsync()
        scope.launch {
            try { ids.forEach { HabitWidgetUpdater.update(context.applicationContext, it) } }
            catch (e: Exception) { Log.w("HabitWidget", "Refresh failed: ${e.javaClass.simpleName}") }
            finally { pending.finish() }
        }
    }
    companion object { private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}

class HabitWidgetTapReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != COMPLETE) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider != ComponentName(context, HabitWidgetReceiver::class.java)) return
        val habit = intent.getStringExtra("habit") ?: return
        val owner = intent.getStringExtra("owner") ?: return
        val day = intent.getLongExtra("day", Long.MIN_VALUE)
        val pending = goAsync()
        val app = context.applicationContext as PixApplication
        scope.launch {
            try {
                if (widgetAccountReady(app)) app.habits.record(habit, day, completeOnly = true, guard = {
                    app.accounts.owner().orEmpty() == owner && LocalDate.now().toEpochDay() == day
                })
            } catch (e: Exception) { Log.w("HabitWidget", "Completion rejected: ${e.javaClass.simpleName}") }
            finally {
                try { HabitWidgetUpdater.updateAll(app) } finally { pending.finish() }
            }
        }
    }
    companion object {
        const val COMPLETE = "com.example.pix.widget.COMPLETE_HABIT"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
