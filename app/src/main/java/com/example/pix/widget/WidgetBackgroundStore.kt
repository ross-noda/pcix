package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Persistent settings keyed by host widget ID, observed even during an active Glance session. */
internal class WidgetBackgroundStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("widget.backgrounds", Context.MODE_PRIVATE)
    fun read(id: Int): Int {
        require(id > 0)
        val key = id.toString()
        if (!prefs.contains(key)) {
            // Preserve choices made by the earlier version, which stored them in host options.
            val legacy = AppWidgetManager.getInstance(app).getAppWidgetOptions(id)
            if (legacy.containsKey(LEGACY_KEY)) {
                check(prefs.edit().putInt(key, legacy.getInt(LEGACY_KEY).coerceIn(0, 2)).commit())
            }
        }
        return prefs.getInt(key, 0).coerceIn(0, 2)
    }
    fun write(id: Int, mode: Int) {
        require(id > 0)
        check(prefs.edit().putInt(id.toString(), mode.coerceIn(0, 2)).commit())
    }
    fun delete(id: Int) { prefs.edit().remove(id.toString()).apply() }
    fun observe(id: Int) = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == id.toString() || key == null) trySend(read(id))
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read(id))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()
    private companion object { const val LEGACY_KEY = "com.example.pix.widget.BACKGROUND" }
}

@Composable
internal fun widgetBackground(context: Context, id: Int): Color {
    val store = remember(context, id) { WidgetBackgroundStore(context) }
    val changes = remember(store, id) { store.observe(id) }
    val mode by changes.collectAsState(initial = store.read(id))
    return widgetBackdrop(mode)
}
