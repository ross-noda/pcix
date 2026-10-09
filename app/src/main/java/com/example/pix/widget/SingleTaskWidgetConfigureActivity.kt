package com.example.pix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.lifecycleScope
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.TaskFilter
import com.example.pix.data.TaskWithDetails
import com.example.pix.ui.theme.PixTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

internal data class SingleTaskPickerState(val tasks: List<TaskWithDetails> = emptyList(), val owner: String? = null, val error: Boolean = false)
class SingleTaskPickerViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private val app = application as PixApplication
    private val mutableState = MutableStateFlow(SingleTaskPickerState())
    internal val state = mutableState.asStateFlow()
    init {
        viewModelScope.launch {
            app.session.state.collectLatest {
                mutableState.value = SingleTaskPickerState()
                if (!widgetAccountReady(app)) return@collectLatest
                val owner = app.accounts.owner().orEmpty()
                app.repository.observe(TaskFilter(mode = "ALL", showCompleted = true), ZonedDateTime.now())
                    .catch { mutableState.value = SingleTaskPickerState(error = true) }
                    .collect { tasks ->
                        mutableState.value = if (singleTaskAccountMatches(app, owner)) SingleTaskPickerState(tasks, owner) else SingleTaskPickerState()
                    }
            }
        }
    }
}

class SingleTaskWidgetConfigureActivity : ComponentActivity() {
    private val model: SingleTaskPickerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        if (AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider != ComponentName(this, SingleTaskWidgetReceiver::class.java)) { finish(); return }
        val app = application as PixApplication
        val prefs = getSharedPreferences("appearance", 0)
        setContent {
            val state by model.state.collectAsState()
            var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
            var selected by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(SingleTaskWidgetStore(this).read(id)?.taskId) }
            var saving by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf(false) }
            PixTheme(prefs.getInt("theme", 0), prefs.getInt("accent", 0xFF5275FF.toInt()), prefs.getInt("textSize", 1), prefs.getInt("fontStyle", 0)) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                        Text(stringResource(R.string.single_task_name), style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.single_task_search)) }, singleLine = true)
                        if (error || state.error) Text(stringResource(R.string.single_task_error), color = MaterialTheme.colorScheme.error)
                        if (state.tasks.isEmpty()) Text(stringResource(R.string.single_task_empty))
                        LazyColumn(Modifier.weight(1f)) {
                            items(state.tasks.filter { it.task.title.contains(query, ignoreCase = true) }, key = { it.task.id }) { detail ->
                                ListItem(headlineContent = { Text(detail.task.title, maxLines = 2) }, supportingContent = { Text(detail.list.name) }, leadingContent = {
                                    RadioButton(selected == detail.task.id, { selected = detail.task.id })
                                })
                            }
                        }
                        Button(enabled = !saving && state.owner != null && state.tasks.any { it.task.id == selected }, onClick = {
                            val taskId = selected ?: return@Button
                            val owner = state.owner ?: return@Button
                            saving = true
                            lifecycleScope.launch {
                                try {
                                    val detail = app.repository.widgetDetails(taskId) { singleTaskAccountMatches(app, owner) }
                                    check(detail != null && !detail.task.isTemplate && !detail.task.isSkipped)
                                    SingleTaskWidgetStore(this@SingleTaskWidgetConfigureActivity).write(id, SingleTaskBinding(taskId, owner))
                                    updateSingleTaskWidget(this@SingleTaskWidgetConfigureActivity, GlanceAppWidgetManager(this@SingleTaskWidgetConfigureActivity).getGlanceIdBy(id))
                                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                    finish()
                                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (_: Exception) { error = true; saving = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.single_task_confirm)) }
                    }
                }
            }
        }
    }
}
