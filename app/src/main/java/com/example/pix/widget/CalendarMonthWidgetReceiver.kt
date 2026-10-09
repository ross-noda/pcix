package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.*

/** No managed Glance session: all refreshes share the same direct, serialized renderer. */
class CalendarMonthWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, ids)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = render(context, intArrayOf(id))
    override fun onDeleted(context: Context, ids: IntArray) {
        super.onDeleted(context, ids)
        ids.forEach { MonthWidgetConfigStore(context).remove(it); WidgetBackgroundStore(context).delete(it) }
        context.getSharedPreferences("widget.month", 0).edit().apply { ids.forEach { remove("offset:$it") } }.apply()
    }
    private fun render(context: Context, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                ids.forEach { id ->
                    try { MonthWidgetUpdater.update(app, id) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { Log.e("MonthWidget", "Refresh failed for widget $id", error) }
                }
            } finally { pending.finish() }
        }
    }
    companion object { private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}
