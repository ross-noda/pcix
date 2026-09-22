package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fast path for purely local calendar navigation.
 *
 * Glance ActionCallback / managed widget sessions are backed by asynchronous worker machinery.
 * That is appropriate for general widget work, but it can add visible cold-start latency to a
 * calendar control. These interactions arrive as explicit foreground broadcasts and render a
 * one-shot RemoteViews snapshot directly in this receiver coroutine instead.
 */
class CalendarWidgetInteractionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId =
            intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                handleInteraction(appContext, appWidgetId, intent)
                Log.d(
                    TAG,
                    "widget=$appWidgetId action=${intent.action} rendered in " +
                        "${SystemClock.elapsedRealtime() - startedAt}ms",
                )
            } catch (throwable: Throwable) {
                Log.e(TAG, "Calendar widget interaction failed", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleInteraction(
        context: Context,
        appWidgetId: Int,
        intent: Intent,
    ) {
        val store = CalendarWidgetConfigStore(context)
        val current = store.read(appWidgetId)
        val selectedEpochDay =
            when (intent.action) {
                ACTION_SELECT_DAY -> {
                    if (!intent.hasExtra(EXTRA_EPOCH_DAY)) return
                    intent.getLongExtra(EXTRA_EPOCH_DAY, current.selectedEpochDay)
                }
                ACTION_PREVIOUS_WEEK ->
                    LocalDate.ofEpochDay(current.selectedEpochDay).minusWeeks(1).toEpochDay()
                ACTION_NEXT_WEEK ->
                    LocalDate.ofEpochDay(current.selectedEpochDay).plusWeeks(1).toEpochDay()
                ACTION_TODAY -> LocalDate.now().toEpochDay()
                else -> return
            }

        if (selectedEpochDay == current.selectedEpochDay) return

        // Persist first so the one-shot composition reads the new day immediately.
        store.write(appWidgetId, current.copy(selectedEpochDay = selectedEpochDay))

        // Render and publish directly to the launcher. This bypasses Glance's managed
        // WorkManager-backed update session for navigation interactions.
        CalendarWeekWidgetUpdater.update(context, appWidgetId)
    }

    companion object {
        const val ACTION_SELECT_DAY = "com.example.pix.widget.action.SELECT_CALENDAR_DAY_FAST"
        const val ACTION_PREVIOUS_WEEK = "com.example.pix.widget.action.PREVIOUS_CALENDAR_WEEK_FAST"
        const val ACTION_NEXT_WEEK = "com.example.pix.widget.action.NEXT_CALENDAR_WEEK_FAST"
        const val ACTION_TODAY = "com.example.pix.widget.action.CALENDAR_TODAY_FAST"
        const val EXTRA_EPOCH_DAY = "calendar_epoch_day"

        private const val TAG = "CalendarWidget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
