package com.example.pix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.ui.theme.PixTheme
import kotlinx.coroutines.launch

class MonthWidgetConfigureActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        setResult(Activity.RESULT_CANCELED, result)
        val store = MonthWidgetConfigStore(this)
        val backgrounds = WidgetBackgroundStore(this)
        setContent {
            var background by rememberSaveable { mutableStateOf(backgrounds.read(id)) }
            val app = application as PixApplication
            val lists by app.repository.lists.collectAsState(emptyList())
            val tags by app.repository.tags.collectAsState(emptyList())
            val calendars by app.google.calendars.collectAsState(emptyList())
            val accounts by app.google.accounts.collectAsState(emptyList())
            var draft by rememberSaveable(id, stateSaver = MonthConfigSaver) { mutableStateOf(store.read(id)) }
            var saving by remember { mutableStateOf(false) }
            var failed by remember { mutableStateOf(false) }
            val prefs = getSharedPreferences("appearance", 0)
            PixTheme(mode = prefs.getInt("theme", 0), accent = prefs.getInt("accent", 0xFF5275FF.toInt()), textSize = prefs.getInt("textSize", 1)) {
                Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.month_widget_content)) }) }, bottomBar = {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { finish() }, enabled = !saving) { Text(stringResource(R.string.cancel)) }
                        Button(onClick = {
                            saving = true; failed = false
                            lifecycleScope.launch {
                                try {
                                    store.write(id, draft)
                                    backgrounds.write(id, background)
                                    MonthWidgetUpdater.update(this@MonthWidgetConfigureActivity, id)
                                    setResult(Activity.RESULT_OK, result); finish()
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (_: Exception) { failed = true; saving = false }
                            }
                        }, enabled = !saving) { Text(stringResource(R.string.save)) }
                    }
                }) { padding ->
                    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { WidgetBackgroundChoices(background) { background = it } }
                        if (failed) item { Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error) }
                        item { Text(stringResource(R.string.month_filter_hint), style = MaterialTheme.typography.bodySmall) }
                        item { Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.month_show_tasks), Modifier.weight(1f)); Switch(draft.showTasks, { draft = draft.copy(showTasks = it) })
                        } }
                        if (draft.showTasks) {
                            item { MonthSelection(stringResource(R.string.lists), lists.map { it.list.id to (it.list.icon + " " + it.list.name) }, draft.listIds) { draft = draft.copy(listIds = it) } }
                            item { MonthSelection(stringResource(R.string.tags), tags.map { it.tag.id to it.tag.name }, draft.tagIds) { draft = draft.copy(tagIds = it) } }
                            item { MonthSelection(stringResource(R.string.priority), listOf(0 to R.string.none, 1 to R.string.low, 3 to R.string.medium, 5 to R.string.high).map { it.first.toString() to stringResource(it.second) }, draft.priorities.map { it.toString() }.toSet()) { draft = draft.copy(priorities = it.map(String::toInt).toSet()) } }
                        }
                        item { HorizontalDivider() }
                        item { Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.month_show_google), Modifier.weight(1f)); Switch(draft.showGoogle, { draft = draft.copy(showGoogle = it) })
                        } }
                        if (draft.showGoogle) item {
                            Text(stringResource(R.string.month_google_hint), style = MaterialTheme.typography.bodySmall)
                            MonthSelection(stringResource(R.string.month_calendars), calendars.filter { it.enabled }.map { c ->
                                monthCalendarKey(c.accountId, c.id) to (c.summary + " · " + (accounts.find { it.id == c.accountId }?.email ?: c.accountId))
                            }, draft.calendarIds) { draft = draft.copy(calendarIds = it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthSelection(title: String, choices: List<Pair<String, String>>, selected: Set<String>, change: (Set<String>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(if (selected.isEmpty()) stringResource(R.string.all) else stringResource(R.string.month_selected, selected.size)) }, modifier = Modifier.clickable { expanded = !expanded })
        if (expanded) {
            Row(Modifier.fillMaxWidth().clickable { change(emptySet()) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(selected.isEmpty(), { change(emptySet()) }); Text(stringResource(R.string.all))
            }
            choices.forEach { (key, label) ->
                Row(Modifier.fillMaxWidth().clickable { change(if (key in selected) selected - key else selected + key) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(key in selected, { change(if (it) selected + key else selected - key) }); Text(label)
                }
            }
        }
    }
}

private val MonthConfigSaver = Saver<MonthWidgetConfig, Bundle>(save = { c ->
    Bundle().apply {
        putBoolean("tasks", c.showTasks); putBoolean("google", c.showGoogle)
        putStringArrayList("lists", ArrayList(c.listIds)); putStringArrayList("tags", ArrayList(c.tagIds))
        putIntegerArrayList("priorities", ArrayList(c.priorities)); putStringArrayList("calendars", ArrayList(c.calendarIds))
    }
}, restore = { b -> MonthWidgetConfig(b.getBoolean("tasks"), b.getBoolean("google"),
    b.getStringArrayList("lists").orEmpty().toSet(), b.getStringArrayList("tags").orEmpty().toSet(),
    b.getIntegerArrayList("priorities").orEmpty().toSet(), b.getStringArrayList("calendars").orEmpty().toSet()) })
