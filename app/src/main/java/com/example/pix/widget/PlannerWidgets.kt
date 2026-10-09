package com.example.pix.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.layout.*
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.text.*
import com.example.pix.MainActivity
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.*
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

private fun plannerAction(context: Context, destination: String, day: Long? = null) = actionStartActivity(
    Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLANNER)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra("destination", destination).apply { day?.let { putExtra(MainActivity.EXTRA_INITIAL_DAY, it) } }
        .setData(Uri.parse("pix://widget/$destination/${day ?: "today"}"))
)

class CalendarMonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PixApplication
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val config = MonthWidgetConfigStore(context).read(widgetId)
        val offset = context.getSharedPreferences("widget.month", 0).getInt("offset:$widgetId", 0)
        val month = YearMonth.now().plusMonths(offset.toLong())
        val days = CalendarRules.days(month)
        val start = days.first().toEpochDay()
        val end = days.last().plusDays(1).toEpochDay()
        val tasks = if (config.showTasks) app.database.dao().observeWidgetRange(start, end).first().filter(config::accepts) else emptyList()
        val events = if (config.showGoogle) app.google.events(start, end).first().filter {
            config.calendarIds.isEmpty() || monthCalendarKey(it.accountId, it.calendarId) in config.calendarIds
        } else emptyList()
        val entries = tasks.map { MonthStrip("t:" + it.task.id, it.task.title, it.task.dueDay!!, TaskTiming.lastDay(it.task)!!,
            TaskWidgetDataSource.palette(it.list.color), taskId = it.task.id) } + events.map {
            MonthStrip("g:" + it.stableKey, it.title, it.startDay, it.endDay - 1, it.colorArgb, google = true)
        }
        val weeks = days.chunked(7).map { week -> week to MonthStrips.lanes(entries, week.first().toEpochDay(), month.atDay(1).toEpochDay(), month.atEndOfMonth().toEpochDay()) }
        provideContent {
            val scale = widgetContentScale(LocalSize.current.width.value, LocalSize.current.height.value, 560f)
            val basePalette = WidgetPalette.forContext(context)
            val palette = basePalette.copy(textScale = basePalette.textScale * scale, surface = Color(0xFF121214), primaryText = Color(0xFFEDEDF0), secondaryText = Color(0xFF98989F), backdrop = widgetBackground(context, widgetId))
            val size = LocalSize.current
            val locale = context.resources.configuration.locales[0]
            val fontScale = palette.textScale * context.resources.configuration.fontScale
            val cellWidth = ((size.width.value - 24f) / 7).coerceAtLeast(1f)
            val weekHeight = ((size.height.value - 26f - 64f * scale) / weeks.size).coerceAtLeast(25f)
            val maxLines = if (weekHeight >= 88 * fontScale) 2 else 1
            val lineHeight = (if (maxLines == 2) 28f else 17f) * fontScale
            val capacity = ((weekHeight - 22 * fontScale - 14 * fontScale) / lineHeight).toInt().coerceAtLeast(0)
            PlannerSurface(palette) {
                Row(GlanceModifier.fillMaxWidth().height((44 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", GlanceModifier.padding((8 * scale).dp).clickable(actionSendBroadcast(monthNavigationIntent(context, widgetId, -1))), style = plannerText(palette, 22f))
                    Text(month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)), GlanceModifier.defaultWeight().clickable(plannerAction(context, "calendar", month.atDay(1).toEpochDay())), style = plannerText(palette, 16f), maxLines = 1)
                    Text("›", GlanceModifier.padding((8 * scale).dp).clickable(actionSendBroadcast(monthNavigationIntent(context, widgetId, 1))), style = plannerText(palette, 22f))
                    Image(ImageProvider(R.drawable.ic_widget_more), context.getString(R.string.month_widget_content), GlanceModifier.size((32 * scale).dp).padding((6 * scale).dp).clickable(actionStartActivity(
                        Intent(context, MonthWidgetConfigureActivity::class.java).putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            .setData(Uri.parse("pix://widget/month/settings/$widgetId")))), colorFilter = ColorFilter.tint(fixedColorProvider(palette.primaryText)))
                }
                Row(GlanceModifier.fillMaxWidth().height((20 * scale).dp)) {
                    DayOfWeek.entries.forEach { Text(it.getDisplayName(JavaTextStyle.SHORT, locale), GlanceModifier.width(cellWidth.dp), style = plannerText(palette, 11f), maxLines = 1) }
                }
                weeks.forEach { (week, lanes) ->
                    Column(GlanceModifier.fillMaxWidth().height(weekHeight.dp)) {
                        Row {
                            week.forEach { date ->
                                Text(date.dayOfMonth.toString(), GlanceModifier.width(cellWidth.dp).height((22 * fontScale).dp).padding(start = (2 * scale).dp)
                                    .clickable(plannerAction(context, "calendar", date.toEpochDay())), style = TextStyle(color = fixedColorProvider(
                                        if (date == LocalDate.now()) palette.accent else if (date.month == month.month) palette.primaryText else palette.secondaryText),
                                        fontWeight = if (date == LocalDate.now()) FontWeight.Bold else FontWeight.Normal, fontSize = (14 * palette.textScale).sp))
                            }
                        }
                        lanes.take(capacity).forEach { lane ->
                            Row(GlanceModifier.fillMaxWidth().height(lineHeight.dp)) {
                                var column = 0
                                lane.forEach { segment ->
                                    if (segment.column > column) Spacer(GlanceModifier.width((cellWidth * (segment.column - column)).dp))
                                    val entry = segment.entry
                                    val color = Color(entry.color or 0xFF000000.toInt())
                                    val filled = entry.google && entry.end > entry.start
                                    val foreground = if (filled) { if (color.luminance() > .179f) Color.Black else Color.White } else palette.primaryText
                                    val click = entry.taskId?.let { taskAction(context, it) } ?: plannerAction(context, "calendar", week.first().toEpochDay() + segment.column)
                                    Box(GlanceModifier.width((cellWidth * segment.span).dp).height(lineHeight.dp).padding(horizontal = (1 * scale).dp, vertical = (1 * scale).dp).clickable(click)) {
                                        Row((if (filled) GlanceModifier.fillMaxWidth().height((13f * fontScale).dp).background(color).cornerRadius((2 * scale).dp) else GlanceModifier.fillMaxSize()), verticalAlignment = Alignment.CenterVertically) {
                                            if (!filled) Box(GlanceModifier.width((2 * scale).dp).fillMaxHeight().background(color)) {}
                                            Text(entry.title, GlanceModifier.defaultWeight().padding(horizontal = (3 * scale).dp), style = TextStyle(color = fixedColorProvider(foreground), fontSize = ((if (filled) 9.5f else 11f) * palette.textScale).sp), maxLines = if (filled || segment.span > 1) 1 else maxLines)
                                        }
                                    }
                                    column = segment.column + segment.span
                                }
                            }
                        }
                        val hidden = MonthStrips.hiddenCounts(lanes, capacity)
                        if (hidden.any { it > 0 }) Row {
                            hidden.forEachIndexed { i, count ->
                                Text(if (count > 0) "+$count" else "", GlanceModifier.width(cellWidth.dp)
                                    .clickable(plannerAction(context, "calendar", week[i].toEpochDay())), style = plannerText(palette, 10f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private val MonthDelta = ActionParameters.Key<Int>("monthDelta")
class MonthNavigation : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        val delta = parameters[MonthDelta]?.coerceIn(-1, 1) ?: return
        MonthWidgetUpdater.update(context, id, delta)
    }
}

class MatrixWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PixApplication
        val config = context.getSharedPreferences("matrix", 0).readMatrixConfig()
        val today = LocalDate.now()
        val tasks = app.repository.observe(TaskFilter(mode = "ALL", rootsOnly = config.hideChildren), ZonedDateTime.now()).first()
        val groups = MatrixRules.groups(tasks, today, config)
        val order = MatrixRules.orderedIds(config)
        provideContent {
            val size = LocalSize.current
            val scale = widgetContentScale(size.width.value, size.height.value, 260f)
            val basePalette = WidgetPalette.forContext(context)
            val palette = basePalette.copy(textScale = basePalette.textScale * scale)
            val dark = palette.surface.luminance() < .3f
            val background = widgetBackground(context, GlanceAppWidgetManager(context).getAppWidgetId(id))
            val surface = if (dark) Color(0xFF1E1E1E) else Color.White
            val dateColor = lerp(palette.accent, if (dark) Color.White else Color.Black, if (dark) .25f else .22f)
            val width = size.width.value - 20f * scale
            val height = (size.height.value - 58f * scale).coerceAtLeast(1f)
            Column(GlanceModifier.fillMaxSize().background(background).appWidgetBackground().padding((10 * scale).dp)) {
                Text(context.getString(R.string.matrix), GlanceModifier.fillMaxWidth().height((38 * scale).dp).clickable(plannerAction(context, "matrix")), style = plannerText(palette, 18f), maxLines = 1)
                @Composable fun card(position: Int, w: Float, h: Float) {
                    val q = order[position]
                    val color = Color(listOf(0xFFEF5964, 0xFFF1BE37, 0xFF607DE2, 0xFF28C6A3)[q])
                    val labels = listOf(R.string.matrix_heading_do, R.string.matrix_heading_plan, R.string.matrix_heading_delegate, R.string.matrix_heading_drop)
                    val title = MatrixRules.card(config, q).title.ifBlank { context.getString(labels[q]) }
                    Column(GlanceModifier.width(w.dp).height(h.dp).background(surface).cornerRadius((config.cornerRadius * scale).dp)) {
                        Row(GlanceModifier.fillMaxWidth().padding(start = (10 * scale).dp, end = (8 * scale).dp, top = (10 * scale).dp, bottom = (8 * scale).dp)
                            .clickable(plannerAction(context, "matrix")), verticalAlignment = Alignment.CenterVertically) {
                            Box(GlanceModifier.size((17 * scale).dp).background(color).cornerRadius((9 * scale).dp), contentAlignment = Alignment.Center) {
                                Text(listOf("I", "II", "III", "IV")[q], style = TextStyle(color = fixedColorProvider(surface), fontSize = (12 * scale).sp))
                            }
                            Spacer(GlanceModifier.width((4 * scale).dp))
                            Text(title, GlanceModifier.defaultWeight(), style = TextStyle(color = fixedColorProvider(color), fontSize = (12 * palette.textScale).sp), maxLines = 1)
                        }
                        LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                            items(groups[q].orEmpty()) { detail ->
                                val task = detail.task
                                Row(GlanceModifier.fillMaxWidth().padding(start = (8 * scale).dp, end = (10 * scale).dp, bottom = (6 * scale).dp), verticalAlignment = Alignment.Top) {
                                    Box(GlanceModifier.width((25 * scale).dp).height((28 * scale).dp).clickable(actionRunCallback<CompleteTaskAction>(
                                        actionParametersOf(TaskIdKey to task.id, TaskCompletedKey to true))), contentAlignment = Alignment.TopCenter) {
                                        Image(ImageProvider(R.drawable.ic_widget_checkbox), context.getString(R.string.widget_complete_task, task.title),
                                            GlanceModifier.padding(top = (2 * scale).dp).size((16 * scale).dp), colorFilter = ColorFilter.tint(fixedColorProvider(if (q == 3) palette.secondaryText else color)))
                                    }
                                    Column(GlanceModifier.defaultWeight().clickable(taskAction(context, task.id))) {
                                        Text(task.title, style = plannerText(palette, 14f), maxLines = 2)
                                        TaskTiming.lastDay(task)?.let { day ->
                                            val date = LocalDate.ofEpochDay(day)
                                            Text(date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", context.resources.configuration.locales[0])),
                                                GlanceModifier.padding(top = (2 * scale).dp), style = TextStyle(color = fixedColorProvider(if (date < today) palette.error else dateColor), fontSize = (10 * palette.textScale).sp), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                when (config.layout) {
                    1 -> repeat(4) { position ->
                        card(position, width, ((height - 30 * scale) * (if (position < 2) config.rowSplit else 1 - config.rowSplit) / 2).coerceAtLeast(40f))
                        if (position < 3) Spacer(GlanceModifier.height((10 * scale).dp))
                    }
                    2 -> Row {
                        repeat(4) { position ->
                            card(position, ((width - 30 * scale) / 4).coerceAtLeast(40f), height)
                            if (position < 3) Spacer(GlanceModifier.width((10 * scale).dp))
                        }
                    }
                    else -> repeat(2) { row ->
                        val h = (height - 10 * scale) * (if (row == 0) config.rowSplit else 1 - config.rowSplit)
                        Row {
                            card(row * 2, (width - 10 * scale) * config.columnSplit, h)
                            Spacer(GlanceModifier.width((10 * scale).dp))
                            card(row * 2 + 1, (width - 10 * scale) * (1 - config.columnSplit), h)
                        }
                        if (row == 0) Spacer(GlanceModifier.height((10 * scale).dp))
                    }
                }
            }
        }
    }
}
class MatrixWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MatrixWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { WidgetBackgroundStore(context).delete(it) }
    }
}

private fun plannerText(palette: WidgetPalette, size: Float) = TextStyle(color = fixedColorProvider(palette.primaryText), fontSize = (size * palette.textScale).sp)
@Composable
private fun PlannerSurface(palette: WidgetPalette, content: @Composable androidx.glance.layout.ColumnScope.() -> Unit) {
    Column(GlanceModifier.fillMaxSize().padding(4.dp).background(palette.backdrop, palette.backdrop)
        .cornerRadius(R.dimen.widget_corner_radius).appWidgetBackground().padding(8.dp), content = content)
}
