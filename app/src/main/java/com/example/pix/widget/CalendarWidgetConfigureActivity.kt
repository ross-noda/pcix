package com.example.pix.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.INBOX_ID
import com.example.pix.data.ListWithCount
import com.example.pix.data.TagWithCount
import com.example.pix.ui.theme.PixTheme
import kotlinx.coroutines.launch

class CalendarWidgetConfigureActivity : ComponentActivity() {
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

        val store = CalendarWidgetConfigStore(this)
        val initial = store.read(appWidgetId)
        setContent {
            val app = application as PixApplication
            val lists by app.repository.lists.collectAsState(initial = emptyList())
            val tags by app.repository.tags.collectAsState(initial = emptyList())
            val themePrefs = remember { getSharedPreferences("appearance", 0) }
            PixTheme(
                mode = themePrefs.getInt("theme", 0),
                accent = themePrefs.getInt("accent", TaskWidgetDataSource.NEUTRAL),
                textSize = themePrefs.getInt("textSize", 1),
                fontStyle = themePrefs.getInt("fontStyle", 0),
            ) {
                CalendarConfigureContent(initial, lists, tags) { config ->
                    lifecycleScope.launch {
                        store.write(appWidgetId, config)
                        CalendarWeekWidgetUpdater.update(
                            this@CalendarWidgetConfigureActivity,
                            appWidgetId,
                        )
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarConfigureContent(
    initial: CalendarWidgetConfig,
    lists: List<ListWithCount>,
    tags: List<TagWithCount>,
    onSave: (CalendarWidgetConfig) -> Unit,
) {
    var filter by remember { mutableStateOf(initial.filterType) }
    var tagIds by remember { mutableStateOf(initial.selectedTagIds) }
    var allTags by remember { mutableStateOf(initial.allTags) }
    var listIds by remember { mutableStateOf(initial.selectedListIds) }
    var allLists by remember { mutableStateOf(initial.allLists) }

    val canSave =
        when (filter) {
            CalendarWidgetFilterType.TAG -> allTags || tagIds.isNotEmpty()
            CalendarWidgetFilterType.LISTS -> allLists || listIds.isNotEmpty()
            else -> true
        }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.calendar_widget_settings)) }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = {
                        onSave(
                            initial.copy(
                                filterType = filter,
                                selectedTagIds = if (allTags) emptySet() else tagIds,
                                allTags = allTags,
                                selectedListIds = if (allLists) emptySet() else listIds,
                                allLists = allLists,
                            )
                        )
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item { CalendarSectionLabel(R.string.widget_filter_title) }
            item {
                CalendarRadioChoice(R.string.all, filter == CalendarWidgetFilterType.ALL) {
                    filter = CalendarWidgetFilterType.ALL
                }
            }
            item {
                CalendarRadioChoice(R.string.widget_filter_tag, filter == CalendarWidgetFilterType.TAG) {
                    filter = CalendarWidgetFilterType.TAG
                    if (!allTags && tagIds.isEmpty()) allTags = true
                }
            }
            if (filter == CalendarWidgetFilterType.TAG) {
                item {
                    CalendarCheckChoice(
                        label = stringResource(R.string.calendar_widget_all_tags),
                        checked = allTags,
                        enabled = true,
                    ) {
                        allTags = it
                        if (it) tagIds = emptySet()
                    }
                }
                items(tags, key = { it.tag.id }) { row ->
                    CalendarCheckChoice(
                        label = "#${row.tag.name}",
                        checked = row.tag.id in tagIds,
                        enabled = !allTags,
                    ) { checked ->
                        tagIds = if (checked) tagIds + row.tag.id else tagIds - row.tag.id
                    }
                }
            }
            item {
                CalendarRadioChoice(R.string.inbox, filter == CalendarWidgetFilterType.INBOX) {
                    filter = CalendarWidgetFilterType.INBOX
                }
            }
            item {
                CalendarRadioChoice(R.string.calendar_widget_lists, filter == CalendarWidgetFilterType.LISTS) {
                    filter = CalendarWidgetFilterType.LISTS
                    if (!allLists && listIds.isEmpty()) allLists = true
                }
            }
            if (filter == CalendarWidgetFilterType.LISTS) {
                item {
                    CalendarCheckChoice(
                        label = stringResource(R.string.calendar_widget_all_lists),
                        checked = allLists,
                        enabled = true,
                    ) {
                        allLists = it
                        if (it) listIds = emptySet()
                    }
                }
                items(lists.filter { it.list.id != INBOX_ID }, key = { it.list.id }) { row ->
                    CalendarCheckChoice(
                        label = "${row.list.icon} ${row.list.name}",
                        checked = row.list.id in listIds,
                        enabled = !allLists,
                    ) { checked ->
                        listIds = if (checked) listIds + row.list.id else listIds - row.list.id
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarSectionLabel(label: Int) {
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun CalendarRadioChoice(label: Int, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(12.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CalendarCheckChoice(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(enabled = enabled) { onCheckedChange(!checked) }
                .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
        )
    }
}
