package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*

internal fun monthNavigationIntent(context: Context, id: Int, delta: Int): Intent {
    require(delta == -1 || delta == 1)
    return Intent(context, MonthWidgetInteractionReceiver::class.java)
        .setAction(if (delta < 0) MonthWidgetInteractionReceiver.PREVIOUS else MonthWidgetInteractionReceiver.NEXT)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        .setData(Uri.parse("pix://widget/month/$id/${if (delta < 0) "previous" else "next"}"))
}

class MonthWidgetInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val delta = when (intent.action) { PREVIOUS -> -1; NEXT -> 1; else -> return }
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try { MonthWidgetUpdater.update(app, id, delta) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { Log.e("MonthWidget", "Month navigation failed for widget $id", error) }
            finally { pending.finish() }
        }
    }
    companion object {
        const val PREVIOUS = "com.example.pix.widget.action.PREVIOUS_MONTH_FAST"
        const val NEXT = "com.example.pix.widget.action.NEXT_MONTH_FAST"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
