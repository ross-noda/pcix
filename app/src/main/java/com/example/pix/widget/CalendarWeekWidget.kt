package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.background
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pix.R

class CalendarWeekWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(180.dp, 150.dp),
                DpSize(280.dp, 220.dp),
                DpSize(360.dp, 360.dp),
            )
        )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        // Load one immutable snapshot before composition. This also makes the public compose()
        // API usable for fast, direct interaction updates without waiting for Glance's managed
        // WorkManager session to wake up.
        val content = CalendarWidgetDataSource(context).load(appWidgetId)

        provideContent {
            CalendarWidgetSurface(
                context = context,
                appWidgetId = appWidgetId,
                content = content,
                palette = WidgetPalette.forContext(context),
            )
        }
    }
}

@Composable
private fun CalendarWidgetSurface(
    context: Context,
    appWidgetId: Int,
    content: CalendarWidgetContent?,
    palette: WidgetPalette,
) {
    val size = LocalSize.current
    val compact = size.width < 250.dp || size.height < 180.dp
    val large = size.width >= 340.dp && size.height >= 300.dp

    Column(
        modifier =
            GlanceModifier.fillMaxSize()
                .background(palette.surface, palette.surface)
                .cornerRadius(R.dimen.widget_corner_radius)
                .appWidgetBackground()
                .padding(
                    horizontal = if (compact) 7.dp else 11.dp,
                    vertical = if (compact) 5.dp else 7.dp,
                ),
    ) {
        CalendarHeader(
            context = context,
            appWidgetId = appWidgetId,
            content = content,
            palette = palette,
            compact = compact,
        )

        if (content == null) {
            CalendarEmptyState(context.getString(R.string.widget_unavailable), palette)
            return@Column
        }

        WeekStrip(context, appWidgetId, content, palette, compact)
        Spacer(GlanceModifier.height(if (compact) 2.dp else 4.dp))
        Box(
            modifier =
                GlanceModifier.fillMaxWidth().height(1.dp).background(palette.divider, palette.divider)
        ) {}
        Spacer(GlanceModifier.height(if (compact) 2.dp else 5.dp))

        if (content.tasks.isEmpty()) {
            CalendarEmptyState(context.getString(R.string.calendar_widget_empty), palette)
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(content.tasks) { task ->
                    CalendarTaskRow(
                        context = context,
                        task = task,
                        palette = palette,
                        compact = compact,
                        large = large,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    context: Context,
    appWidgetId: Int,
    content: CalendarWidgetContent?,
    palette: WidgetPalette,
    compact: Boolean,
) {
    val selectedDay = content?.config?.selectedEpochDay
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(if (compact) 34.dp else 38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CalendarIconButton(
            drawable = R.drawable.ic_widget_calendar,
            description = context.getString(R.string.go_today),
            palette = palette,
            size = if (compact) 28.dp else 32.dp,
            iconSize = if (compact) 18.dp else 20.dp,
            action = calendarTodayAction(context, appWidgetId),
        )
        CalendarIconButton(
            drawable = R.drawable.ic_widget_chevron_left,
            description = context.getString(R.string.previous_week),
            palette = palette,
            size = if (compact) 27.dp else 31.dp,
            iconSize = 18.dp,
            action = calendarPreviousWeekAction(context, appWidgetId),
        )
        Text(
            text = content?.monthLabel.orEmpty(),
            modifier = GlanceModifier.defaultWeight().padding(horizontal = 2.dp),
            maxLines = 1,
            style =
                TextStyle(
                    color = fixedColorProvider(palette.primaryText),
                    fontSize = ((if (compact) 14f else 17f) * palette.textScale).sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
        CalendarIconButton(
            drawable = R.drawable.ic_widget_chevron_right,
            description = context.getString(R.string.next_week),
            palette = palette,
            size = if (compact) 27.dp else 31.dp,
            iconSize = 18.dp,
            action = calendarNextWeekAction(context, appWidgetId),
        )
        CalendarIconButton(
            drawable = R.drawable.ic_widget_add,
            description = context.getString(R.string.widget_add_task),
            palette = palette,
            size = if (compact) 28.dp else 32.dp,
            iconSize = if (compact) 18.dp else 20.dp,
            action = newTaskAction(context, selectedDay),
        )
        if (!compact) {
            CalendarIconButton(
                drawable = R.drawable.ic_widget_more,
                description = context.getString(R.string.widget_more_options),
                palette = palette,
                size = 32.dp,
                iconSize = 18.dp,
                action = calendarConfigAction(context, appWidgetId),
            )
        }
    }
}

@Composable
private fun CalendarIconButton(
    drawable: Int,
    description: String,
    palette: WidgetPalette,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    action: androidx.glance.action.Action,
) {
    Box(
        modifier = GlanceModifier.size(size).clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(drawable),
            contentDescription = description,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(fixedColorProvider(palette.primaryText)),
        )
    }
}

@Composable
private fun WeekStrip(
    context: Context,
    appWidgetId: Int,
    content: CalendarWidgetContent,
    palette: WidgetPalette,
    compact: Boolean,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(if (compact) 50.dp else 58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content.days.forEach { day ->
            val selectDayAction =
                calendarSelectDayAction(
                    context = context,
                    appWidgetId = appWidgetId,
                    epochDay = day.epochDay,
                )

            Box(
                modifier =
                    GlanceModifier.defaultWeight()
                        .fillMaxHeight()
                        .semantics {
                            contentDescription =
                                context.getString(
                                    R.string.calendar_widget_select_day,
                                    day.weekdayLabel,
                                    day.dayNumber,
                                )
                        }
                        .clickable(selectDayAction),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = day.weekdayLabel,
                        maxLines = 1,
                        style =
                            TextStyle(
                                color = fixedColorProvider(palette.secondaryText),
                                fontSize = ((if (compact) 8.5f else 10f) * palette.textScale).sp,
                                fontWeight = if (day.today) FontWeight.Bold else FontWeight.Normal,
                            ),
                    )
                    Spacer(GlanceModifier.height(1.dp))
                    val dayBackground = if (day.selected) palette.accent else palette.surface
                    Box(
                        modifier =
                            GlanceModifier.size(if (compact) 22.dp else 32.dp)
                                // Always write a background value. RemoteViews re-application can
                                // otherwise retain the previous selected-day background when a
                                // modifier disappears on the next update.
                                .background(dayBackground, dayBackground)
                                .cornerRadius(if (compact) 11.dp else 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.dayNumber.toString(),
                            maxLines = 1,
                            style =
                                TextStyle(
                                    color =
                                        fixedColorProvider(
                                            when {
                                                day.selected -> palette.onAccent
                                                day.today -> palette.accent
                                                else -> palette.primaryText
                                            }
                                        ),
                                    fontSize = ((if (compact) 13f else 15f) * palette.textScale).sp,
                                    fontWeight =
                                        if (day.selected || day.today) FontWeight.Bold
                                        else FontWeight.Normal,
                                ),
                        )
                    }
                    Spacer(GlanceModifier.height(if (compact) 1.dp else 2.dp))
                    DayTaskMarkers(day.markerColorsArgb, palette, compact)
                }
            }
        }
    }
}

@Composable
private fun DayTaskMarkers(
    markerColorsArgb: List<Int>,
    palette: WidgetPalette,
    compact: Boolean,
) {
    val dotSize = if (compact) 3.dp else 4.dp
    val gap = if (compact) 1.dp else 2.dp
    Row(
        modifier = GlanceModifier.height(if (compact) 4.dp else 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keep exactly three marker slots in the RemoteViews tree. Conditional children can leave
        // stale dots behind on some launchers when the same Glance hierarchy is re-applied.
        repeat(3) { index ->
            if (index > 0) Spacer(GlanceModifier.width(gap))
            val markerColor =
                markerColorsArgb.getOrNull(index)?.let(::Color) ?: palette.surface
            Box(
                modifier =
                    GlanceModifier.size(dotSize)
                        .background(markerColor, markerColor)
                        .cornerRadius(2.dp),
            ) {}
        }
    }
}

@Composable
private fun CalendarTaskRow(
    context: Context,
    task: CalendarWidgetTask,
    palette: WidgetPalette,
    compact: Boolean,
    large: Boolean,
) {
    val rowHeight = if (compact) 26.dp else if (large) 30.dp else 28.dp
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier.width(3.dp)
                    .fillMaxHeight()
                    .background(Color(task.listColorArgb), Color(task.listColorArgb))
        ) {}
        Spacer(GlanceModifier.width(4.dp))
        Box(
            modifier =
                GlanceModifier.width(if (compact) 28.dp else 31.dp)
                    .height(rowHeight)
                    .clickable(
                        actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(
                                TaskIdKey to task.id,
                                TaskCompletedKey to !task.isCompleted,
                            )
                        )
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider =
                    ImageProvider(
                        if (task.isCompleted) R.drawable.ic_widget_checked
                        else R.drawable.ic_widget_checkbox
                    ),
                contentDescription =
                    context.getString(
                        if (task.isCompleted) R.string.widget_reopen_task else R.string.widget_complete_task,
                        task.title,
                    ),
                modifier = GlanceModifier.size(if (compact) 16.dp else 18.dp),
                colorFilter =
                    ColorFilter.tint(
                        fixedColorProvider(
                            if (task.isCompleted) palette.secondaryText
                            else Color(TaskWidgetDataSource.priorityColor(task.priority))
                        )
                    ),
            )
        }
        Spacer(GlanceModifier.width(2.dp))
        Row(
            modifier =
                GlanceModifier.defaultWeight()
                    .fillMaxHeight()
                    .clickable(taskAction(context, task.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = task.title,
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style =
                    TextStyle(
                        color =
                            fixedColorProvider(
                                if (task.isCompleted) palette.secondaryText else palette.primaryText
                            ),
                        fontSize = ((if (compact) 11.5f else 13f) * palette.textScale).sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )
            if (!compact && task.timeLabel.isNotBlank()) {
                Spacer(GlanceModifier.width(5.dp))
                Text(
                    text = task.timeLabel,
                    maxLines = 1,
                    style =
                        TextStyle(
                            color = fixedColorProvider(palette.secondaryText),
                            fontSize = (10.5f * palette.textScale).sp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun CalendarEmptyState(text: String, palette: WidgetPalette) {
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            maxLines = 2,
            style =
                TextStyle(
                    color = fixedColorProvider(palette.secondaryText),
                    fontSize = (13f * palette.textScale).sp,
                ),
        )
    }
}

private fun calendarSelectDayAction(
    context: Context,
    appWidgetId: Int,
    epochDay: Long,
) =
    actionSendBroadcast(
        Intent(context, CalendarWidgetInteractionReceiver::class.java)
            .setAction(CalendarWidgetInteractionReceiver.ACTION_SELECT_DAY)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(CalendarWidgetInteractionReceiver.EXTRA_EPOCH_DAY, epochDay)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .setData(Uri.parse("pix://widget/calendar/$appWidgetId/day/$epochDay"))
    )

private fun calendarPreviousWeekAction(context: Context, appWidgetId: Int) =
    actionSendBroadcast(
        Intent(context, CalendarWidgetInteractionReceiver::class.java)
            .setAction(CalendarWidgetInteractionReceiver.ACTION_PREVIOUS_WEEK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .setData(Uri.parse("pix://widget/calendar/$appWidgetId/week/previous"))
    )

private fun calendarNextWeekAction(context: Context, appWidgetId: Int) =
    actionSendBroadcast(
        Intent(context, CalendarWidgetInteractionReceiver::class.java)
            .setAction(CalendarWidgetInteractionReceiver.ACTION_NEXT_WEEK)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .setData(Uri.parse("pix://widget/calendar/$appWidgetId/week/next"))
    )

private fun calendarTodayAction(context: Context, appWidgetId: Int) =
    actionSendBroadcast(
        Intent(context, CalendarWidgetInteractionReceiver::class.java)
            .setAction(CalendarWidgetInteractionReceiver.ACTION_TODAY)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .setData(Uri.parse("pix://widget/calendar/$appWidgetId/today"))
    )

private fun calendarConfigAction(context: Context, appWidgetId: Int) =
    actionStartActivity(
        Intent(context, CalendarWidgetConfigureActivity::class.java)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .setData(Uri.parse("pix://widget/calendar/config/$appWidgetId"))
    )
