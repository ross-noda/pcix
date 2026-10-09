package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.semantics.*
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.TaskWithDetails
import com.example.pix.domain.DescriptionText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SingleTaskWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = loadSingleTask(context, widgetId)
        provideContent {
            val revision = currentState<Preferences>()[SingleTaskRevision]
            val snapshot by produceState(initial, revision) { value = loadSingleTask(context, widgetId) }
            SingleTaskContent(context, widgetId, snapshot.binding, snapshot.detail, snapshot.failed)
        }
    }

}

private val SingleTaskRevision = longPreferencesKey("single_task_revision")
private data class SingleTaskSnapshot(val binding: SingleTaskBinding?, val detail: TaskWithDetails?, val failed: Boolean = false)
private suspend fun loadSingleTask(context: Context, id: Int): SingleTaskSnapshot {
    val app = context.applicationContext as PixApplication
    val binding = SingleTaskWidgetStore(context).read(id)
    return try {
        val detail = if (binding != null && singleTaskAccountMatches(app, binding.owner)) app.repository.widgetDetails(binding.taskId) {
            singleTaskAccountMatches(app, binding.owner)
        }?.takeUnless { it.task.isSkipped || it.task.isTemplate } else null
        SingleTaskSnapshot(binding, detail)
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { SingleTaskSnapshot(binding, null, true) }
}

/** A state revision also reloads data while a Glance composition is already alive. */
internal suspend fun updateSingleTaskWidget(context: Context, id: GlanceId) {
    updateAppWidgetState(context, id) { it[SingleTaskRevision] = (it[SingleTaskRevision] ?: 0L) + 1 }
    SingleTaskWidget().update(context, id)
}
internal suspend fun updateSingleTaskWidgets(context: Context) {
    GlanceAppWidgetManager(context).getGlanceIds(SingleTaskWidget::class.java).forEach {
        updateSingleTaskWidget(context, it)
    }
}

internal fun singleTaskConfigIntent(context: Context, id: Int) =
    Intent(context, SingleTaskWidgetConfigureActivity::class.java)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .setData(Uri.parse("pix://widget/single/config/$id"))

@Composable
internal fun SingleTaskContent(context: Context, id: Int, binding: SingleTaskBinding?, detail: TaskWithDetails?, failed: Boolean) {
    val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    val dark = prefs.getInt("theme", 0).let { it == 2 || (it == 0 && context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) }
    val palette = WidgetPalette.forContext(context).let {
        if (dark) it else it.copy(surface = Color.White, primaryText = Color(0xFF181A20), secondaryText = Color(0xFF666B76), accent = androidx.compose.ui.graphics.lerp(it.accent, Color.Black, .22f))
    }
    val open = if (detail != null) taskAction(context, detail.task.id) else actionStartActivity(singleTaskConfigIntent(context, id))
    Box(GlanceModifier.fillMaxSize().background(palette.surface).cornerRadius(24.dp).appWidgetBackground().clickable(open).padding(12.dp)) {
        if (detail == null || binding == null) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text(context.getString(if (failed) R.string.single_task_error else R.string.single_task_missing), style = TextStyle(color = fixedColorProvider(palette.primaryText), fontSize = 17.sp, fontWeight = FontWeight.Bold))
                Text(context.getString(R.string.single_task_choose), style = TextStyle(color = fixedColorProvider(palette.secondaryText), fontSize = 14.sp))
            }
        } else {
            val task = detail.task
            val rows = DescriptionText.items(task.notes)
            val notesRevision = DescriptionText.revision(task.notes)
            val checklist = rows.withIndex().filter { it.value.checked != null }
            val children = detail.visibleChildren.sortedWith(compareBy({ it.isCompleted }, { it.sortOrder }, { it.id }))
            // A Glance collection supplies native, reliable scrolling without shrinking touch targets.
            LazyColumn(GlanceModifier.fillMaxSize()) {
                item {
                    Text(task.title, GlanceModifier.fillMaxWidth().clickable(open).semantics { contentDescription = context.getString(R.string.single_task_open, task.title) }, maxLines = 3,
                        style = TextStyle(color = fixedColorProvider(if (task.isCompleted) palette.secondaryText else palette.primaryText), fontSize = (19 * palette.textScale).sp, fontWeight = FontWeight.Bold,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None))
                }
                item {
                    val locale = context.resources.configuration.locales[0]
                    val date = task.dueDay?.let { LocalDate.ofEpochDay(it).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)) }
                    val time = task.minuteOfDay?.let { LocalTime.of(it / 60, it % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)) }
                    Text(listOfNotNull(date, time, detail.list.name).joinToString(" · "), GlanceModifier.fillMaxWidth().clickable(open).padding(vertical = 8.dp), maxLines = 3,
                        style = TextStyle(color = fixedColorProvider(palette.secondaryText), fontSize = (12 * palette.textScale).sp))
                }
                checklist.take(20).forEach { (index, row) ->
                    if (row.checked != null) item {
                        SingleTaskRow(context, row.text, row.checked, palette, open,
                            actionRunCallback<SingleTaskCheckAction>(actionParametersOf(SingleWidgetKey to id, SingleOwnerKey to binding.owner, SingleParentKey to task.id, SingleIndexKey to index, SingleNotesKey to notesRevision, TaskCompletedKey to !row.checked)))
                    }
                }
                if (checklist.size > 20) item { MoreTasks(context, checklist.size - 20, palette, open) }
                if (children.isNotEmpty()) item {
                    Text(context.getString(R.string.single_task_children), GlanceModifier.fillMaxWidth().clickable(open).padding(top = 10.dp), style = TextStyle(color = fixedColorProvider(palette.secondaryText), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
                children.take(20).forEach { child -> item {
                    SingleTaskRow(context, child.title, child.isCompleted, palette, taskAction(context, child.id),
                        actionRunCallback<SingleTaskCheckAction>(actionParametersOf(SingleWidgetKey to id, SingleOwnerKey to binding.owner, SingleParentKey to task.id, TaskIdKey to child.id, TaskCompletedKey to !child.isCompleted)), true)
                } }
                if (children.size > 20) item { MoreTasks(context, children.size - 20, palette, open) }
                val notes = rows.filter { it.checked == null }.joinToString("\n") { it.text }.trim()
                if (notes.isNotBlank()) item {
                    Text(notes.take(2000), GlanceModifier.fillMaxWidth().clickable(open).padding(top = 8.dp), maxLines = 4, style = TextStyle(color = fixedColorProvider(palette.secondaryText), fontSize = (13 * palette.textScale).sp))
                }
            }
        }
    }
}

@Composable
private fun MoreTasks(context: Context, count: Int, palette: WidgetPalette, open: Action) {
    Text(context.resources.getQuantityString(R.plurals.single_task_more, count, count), GlanceModifier.fillMaxWidth().height(48.dp).clickable(open),
        style = TextStyle(color = fixedColorProvider(palette.accent), fontSize = 14.sp))
}

@Composable
private fun SingleTaskRow(context: Context, title: String, checked: Boolean, palette: WidgetPalette, open: Action, toggle: Action, child: Boolean = false) {
    val rowHeight = (48 * context.resources.configuration.fontScale.coerceIn(1f, 2f)).dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size(48.dp).clickable(toggle).semantics { contentDescription = context.getString(if (checked) R.string.single_task_reopen else R.string.widget_complete_task, title) }, contentAlignment = Alignment.Center) {
            Image(ImageProvider(if (checked) R.drawable.ic_widget_checked else R.drawable.ic_widget_checkbox), null, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(fixedColorProvider(palette.accent)))
        }
        Box(GlanceModifier.defaultWeight().height(rowHeight).clickable(open).semantics { contentDescription = context.getString(if (child) R.string.single_task_open_child else R.string.single_task_open, title) }, contentAlignment = Alignment.CenterStart) {
            Text(if (child) "→ $title" else title, maxLines = 2, style = TextStyle(color = fixedColorProvider(if (checked) palette.secondaryText else if (child) palette.accent else palette.primaryText), fontSize = (14 * palette.textScale).sp,
                textDecoration = if (checked) TextDecoration.LineThrough else if (child) TextDecoration.Underline else TextDecoration.None))
        }
    }
}

private val SingleWidgetKey = ActionParameters.Key<Int>("single_widget")
private val SingleOwnerKey = ActionParameters.Key<String>("single_owner")
private val SingleParentKey = ActionParameters.Key<String>("single_parent")
private val SingleIndexKey = ActionParameters.Key<Int>("single_index")
private val SingleNotesKey = ActionParameters.Key<String>("single_notes")
class SingleTaskCheckAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as PixApplication
        val id = parameters[SingleWidgetKey] ?: return
        val owner = parameters[SingleOwnerKey] ?: return
        val parent = parameters[SingleParentKey] ?: return
        val checked = parameters[TaskCompletedKey] ?: return
        val binding = SingleTaskBinding(parent, owner)
        val guard = { singleTaskAccountMatches(app, owner) && SingleTaskWidgetStore(context).read(id) == binding }
        try {
            if (GlanceAppWidgetManager(context).getAppWidgetId(glanceId) != id || !widgetAccountReady(app)) return
            val child = parameters[TaskIdKey]
            if (child != null) app.repository.completeWidgetChild(parent, child, checked, guard)
            else app.repository.setChecklistItem(parent, parameters[SingleNotesKey] ?: return, parameters[SingleIndexKey] ?: return, checked, guard)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { android.util.Log.w("SingleTaskWidget", "Action rejected; refreshing persisted state") }
        finally { updateAllPixWidgets(context) }
    }
}

class SingleTaskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SingleTaskWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { SingleTaskWidgetStore(context).delete(it) }
    }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        // Device restore requires explicit selection again; never reuse another account's binding.
        oldWidgetIds.forEach { SingleTaskWidgetStore(context).delete(it) }
        newWidgetIds.forEach { SingleTaskWidgetStore(context).delete(it) }
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }
}
