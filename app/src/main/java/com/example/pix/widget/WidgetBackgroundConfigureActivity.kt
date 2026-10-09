package com.example.pix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pix.R
import com.example.pix.ui.theme.PixTheme
import kotlinx.coroutines.launch

@Composable
internal fun WidgetBackgroundChoices(selected: Int, change: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.widget_background_title), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleMedium)
        listOf(R.string.widget_background_dark, R.string.widget_background_semi, R.string.widget_background_clear).forEachIndexed { index, label ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == index, role = Role.RadioButton, onClick = { change(index) }), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected == index, onClick = null)
                Text(stringResource(label), Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** Habit and Matrix have no filter configuration; Android exposes this through Edit widget. */
class WidgetBackgroundConfigureActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider
        val habit = ComponentName(this, HabitWidgetReceiver::class.java)
        val matrix = ComponentName(this, MatrixWidgetReceiver::class.java)
        setResult(Activity.RESULT_CANCELED)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || provider !in listOf(habit, matrix)) { finish(); return }
        val store = WidgetBackgroundStore(this)
        setContent {
            var selected by rememberSaveable { mutableStateOf(store.read(id)) }
            var saving by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            val prefs = getSharedPreferences("appearance", 0)
            PixTheme(mode = prefs.getInt("theme", 0), accent = prefs.getInt("accent", 0xFF5275FF.toInt()), textSize = prefs.getInt("textSize", 1)) {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_settings)) }) }, bottomBar = {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { finish() }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                        Button(enabled = !saving, onClick = {
                            saving = true
                            failed = false
                            lifecycleScope.launch {
                                try {
                                    store.write(id, selected)
                                    if (provider == habit) HabitWidgetUpdater.update(this@WidgetBackgroundConfigureActivity, id)
                                    else MatrixWidget().update(this@WidgetBackgroundConfigureActivity, GlanceAppWidgetManager(this@WidgetBackgroundConfigureActivity).getGlanceIdBy(id))
                                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                    finish()
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { failed = true; saving = false }
                            }
                        }) { Text(stringResource(R.string.save)) }
                    }
                }) { padding ->
                    Column(Modifier.padding(padding)) {
                        WidgetBackgroundChoices(selected) { selected = it }
                        if (failed) Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
