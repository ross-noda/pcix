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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.ListWithCount
import com.example.pix.data.TagWithCount
import com.example.pix.ui.theme.PixTheme
import kotlinx.coroutines.launch

class TaskWidgetConfigureActivity : ComponentActivity() {
    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
        )
        val mode =
            runCatching {
                    WidgetConfigMode.valueOf(
                        intent?.getStringExtra(EXTRA_MODE) ?: WidgetConfigMode.FULL.name
                    )
                }
                .getOrDefault(WidgetConfigMode.FULL)
        val store = TaskWidgetConfigStore(this)
        val initial = store.read(appWidgetId)
        setContent {
            val app = application as PixApplication
            val lists by app.repository.lists.collectAsState(initial = emptyList())
            val tags by app.repository.tags.collectAsState(initial = emptyList())
            val themePrefs = remember { getSharedPreferences("appearance", 0) }
            PixTheme(
                mode = themePrefs.getInt("theme", 0),
                accent = themePrefs.getInt("accent", 0xFF5275FF.toInt()),
                textSize = themePrefs.getInt("textSize", 1),
                fontStyle = themePrefs.getInt("fontStyle", 0),
            ) {
                ConfigureContent(mode, initial, lists, tags) { config ->
                    lifecycleScope.launch {
                        store.write(appWidgetId, config)
                        val glanceId = GlanceAppWidgetManager(this@TaskWidgetConfigureActivity).getGlanceIdBy(appWidgetId)
                        TaskWidget().update(this@TaskWidgetConfigureActivity, glanceId)
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        )
                        finish()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "widget_config_mode"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigureContent(
    mode: WidgetConfigMode,
    initial: TaskWidgetConfig,
    lists: List<ListWithCount>,
    tags: List<TagWithCount>,
    onSave: (TaskWidgetConfig) -> Unit,
) {
    var filter by remember { mutableStateOf(initial.filterType) }
    var entityId by remember { mutableStateOf(initial.filterEntityId) }
    var groupBy by remember { mutableStateOf(initial.groupBy) }
    val title =
        when (mode) {
            WidgetConfigMode.FILTER -> R.string.widget_filter_title
            WidgetConfigMode.GROUPING -> R.string.widget_group_title
            WidgetConfigMode.FULL -> R.string.widget_settings
        }

    Scaffold(
        topBar = { TopAppBar(title = { Text(androidx.compose.ui.res.stringResource(title)) }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSave(TaskWidgetConfig(filter, entityId, groupBy)) },
                    enabled =
                        when (filter) {
                            WidgetFilterType.LIST, WidgetFilterType.TAG -> entityId != null
                            else -> true
                        },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(androidx.compose.ui.res.stringResource(R.string.done))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            if (mode != WidgetConfigMode.GROUPING) {
                item { SectionLabel(R.string.widget_filter_title) }
                item {
                    ChoiceRow(R.string.all, filter == WidgetFilterType.ALL) {
                        filter = WidgetFilterType.ALL
                        entityId = null
                    }
                }
                item {
                    ChoiceRow(R.string.today, filter == WidgetFilterType.TODAY) {
                        filter = WidgetFilterType.TODAY
                        entityId = null
                    }
                }
                item {
                    ChoiceRow(R.string.tomorrow, filter == WidgetFilterType.TOMORROW) {
                        filter = WidgetFilterType.TOMORROW
                        entityId = null
                    }
                }
                item {
                    ChoiceRow(R.string.week, filter == WidgetFilterType.NEXT_7_DAYS) {
                        filter = WidgetFilterType.NEXT_7_DAYS
                        entityId = null
                    }
                }
                item {
                    ChoiceRow(R.string.widget_filter_list, filter == WidgetFilterType.LIST) {
                        filter = WidgetFilterType.LIST
                        if (lists.none { it.list.id == entityId }) entityId = lists.firstOrNull()?.list?.id
                    }
                }
                if (filter == WidgetFilterType.LIST) {
                    items(lists, key = { it.list.id }) { row ->
                        EntityChoice("${row.list.icon} ${row.list.name}", row.list.id == entityId) {
                            entityId = row.list.id
                        }
                    }
                }
                item {
                    ChoiceRow(R.string.widget_filter_tag, filter == WidgetFilterType.TAG) {
                        filter = WidgetFilterType.TAG
                        if (tags.none { it.tag.id == entityId }) entityId = tags.firstOrNull()?.tag?.id
                    }
                }
                if (filter == WidgetFilterType.TAG) {
                    items(tags, key = { it.tag.id }) { row ->
                        EntityChoice("#${row.tag.name}", row.tag.id == entityId) { entityId = row.tag.id }
                    }
                }
            }

            if (mode != WidgetConfigMode.FILTER) {
                item { SectionLabel(R.string.widget_group_title) }
                item { GroupChoice(R.string.widget_group_list, WidgetGroupBy.LIST, groupBy) { groupBy = it } }
                item { GroupChoice(R.string.widget_group_date, WidgetGroupBy.DATE, groupBy) { groupBy = it } }
                item { GroupChoice(R.string.widget_group_tag, WidgetGroupBy.TAG, groupBy) { groupBy = it } }
                item { GroupChoice(R.string.widget_group_created, WidgetGroupBy.CREATED_AT, groupBy) { groupBy = it } }
                item { GroupChoice(R.string.priority, WidgetGroupBy.PRIORITY, groupBy) { groupBy = it } }
                item { GroupChoice(R.string.widget_group_none, WidgetGroupBy.NONE, groupBy) { groupBy = it } }
            }
        }
    }
}

@Composable
private fun SectionLabel(label: Int) {
    Text(
        text = androidx.compose.ui.res.stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun ChoiceRow(label: Int, selected: Boolean, onClick: () -> Unit) {
    EntityChoice(androidx.compose.ui.res.stringResource(label), selected, onClick)
}

@Composable
private fun GroupChoice(
    label: Int,
    value: WidgetGroupBy,
    selected: WidgetGroupBy,
    onClick: (WidgetGroupBy) -> Unit,
) {
    EntityChoice(androidx.compose.ui.res.stringResource(label), value == selected) { onClick(value) }
}

@Composable
private fun EntityChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
