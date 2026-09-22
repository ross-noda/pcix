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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.pix.R
import androidx.glance.color.ColorProvider

class TaskWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode =
        SizeMode.Responsive(
            setOf(
                DpSize(180.dp, 120.dp),
                DpSize(280.dp, 220.dp),
                DpSize(360.dp, 360.dp),
            )
        )

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appWidgetId =
            GlanceAppWidgetManager(context).getAppWidgetId(id)

        val content =
            runCatching {
                TaskWidgetDataSource(context).load(appWidgetId)
            }.getOrNull()

        provideContent {
            val palette = WidgetPalette.forContext(context)

            WidgetSurface(
                context = context,
                appWidgetId = appWidgetId,
                content = content,
                palette = palette,
            )
        }
    }
}

@Composable
private fun WidgetSurface(
    context: Context,
    appWidgetId: Int,
    content: WidgetContent?,
    palette: WidgetPalette,
) {
    val size = LocalSize.current

    val compact =
        size.width < 250.dp ||
                size.height < 180.dp

    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(
                    palette.surface,
                    palette.surface,
                )
                .cornerRadius(R.dimen.widget_corner_radius)
                .appWidgetBackground()
                .padding(
                    horizontal = if (compact) 8.dp else 11.dp,
                    vertical = 6.dp,
                ),
    ) {

        Header(
            context = context,
            appWidgetId = appWidgetId,
            label =
                content?.filterLabel
                    ?: context.getString(R.string.all),
            palette = palette,
            compact = compact,
        )

        Spacer(
            GlanceModifier.height(
                if (compact) 1.dp else 3.dp
            )
        )

        if (content == null) {
            EmptyState(
                context.getString(R.string.widget_unavailable),
                palette,
            )
            return@Column
        }

        val totalTasks =
            content.groups.sumOf { it.tasks.size }

        if (totalTasks == 0) {
            EmptyState(
                content.emptyLabel,
                palette,
            )
            return@Column
        }

        LazyColumn(
            modifier = GlanceModifier.fillMaxSize()
        ) {

            content.groups.forEach { group ->

                if (
                    !compact &&
                    content.config.groupBy != WidgetGroupBy.NONE &&
                    group.tasks.isNotEmpty()
                ) {
                    item {
                        GroupHeader(
                            group = group,
                            palette = palette,
                        )
                    }
                }

                items(group.tasks) { task ->
                    TaskRow(
                        context = context,
                        task = task,
                        groupColor = group.colorArgb,
                        palette = palette,
                        compact = compact,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    context: Context,
    appWidgetId: Int,
    label: String,
    palette: WidgetPalette,
    compact: Boolean,
) {

    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        Text(
            text =
                if (compact) {
                    label
                } else {
                    "$label  ˅"
                },
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .padding(
                        start = 2.dp,
                        end = 2.dp,
                    )
                    .clickable(
                        configAction(
                            context,
                            appWidgetId,
                            WidgetConfigMode.FILTER,
                        )
                    ),
            maxLines = 1,
            style =
                TextStyle(
                    color =
                        ColorProvider(
                            day = palette.primaryText,
                            night = palette.primaryText,
                        ),
                    fontSize =
                        (
                                (
                                        if (compact) {
                                            17f
                                        } else {
                                            20f
                                        }
                                        ) * palette.textScale
                                ).sp,
                    fontWeight = FontWeight.Bold,
                ),
        )

        Box(
            modifier =
                GlanceModifier
                    .size(36.dp)
                    .clickable(
                        newTaskAction(context)
                    ),
            contentAlignment = Alignment.Center,
        ) {

            Image(
                provider =
                    ImageProvider(
                        R.drawable.ic_widget_add
                    ),
                contentDescription =
                    context.getString(
                        R.string.widget_add_task
                    ),
                modifier =
                    GlanceModifier.size(22.dp),
                colorFilter =
                    ColorFilter.tint(
                        ColorProvider(
                            day = palette.primaryText,
                            night = palette.primaryText,
                        )
                    ),
            )
        }

        if (!compact) {

            Box(
                modifier =
                    GlanceModifier
                        .size(36.dp)
                        .clickable(
                            configAction(
                                context,
                                appWidgetId,
                                WidgetConfigMode.GROUPING,
                            )
                        ),
                contentAlignment = Alignment.Center,
            ) {

                Image(
                    provider =
                        ImageProvider(
                            R.drawable.ic_widget_more
                        ),
                    contentDescription =
                        context.getString(
                            R.string.widget_more_options
                        ),
                    modifier =
                        GlanceModifier.size(20.dp),
                    colorFilter =
                        ColorFilter.tint(
                            ColorProvider(
                                day = palette.primaryText,
                                night = palette.primaryText,
                            )
                        ),
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(
    group: WidgetGroup,
    palette: WidgetPalette,
) {

    Text(
        text = group.title,
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(
                    top = 3.dp,
                    bottom = 1.dp,
                    start = 2.dp,
                ),
        maxLines = 1,
        style =
            TextStyle(
                color =
                    ColorProvider(
                        day = palette.secondaryText,
                        night = palette.secondaryText,
                    ),
                fontSize =
                    (11f * palette.textScale).sp,
                fontWeight = FontWeight.Medium,
            ),
    )
}

@Composable
private fun TaskRow(
    context: Context,
    task: WidgetTask,
    groupColor: Int,
    palette: WidgetPalette,
    compact: Boolean,
) {

    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(
                    if (compact) {
                        27.dp
                    } else {
                        29.dp
                    }
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {

        /*
         * Barra verticale del gruppo.
         *
         * È più sottile della versione precedente in modo da
         * conservare la gerarchia cromatica senza rubare spazio.
         */
        Box(
            modifier =
                GlanceModifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        Color(groupColor),
                        Color(groupColor),
                    )
        ) {}

        Spacer(
            GlanceModifier.width(5.dp)
        )

        /*
         * Area checkbox.
         *
         * Invece della vecchia Image 48x48 con padding,
         * utilizziamo un contenitore compatto.
         *
         * L'icona rimane chiaramente visibile ma la riga
         * non viene forzata ad avere 48 dp di altezza.
         */
        Box(
            modifier =
                GlanceModifier
                    .width(32.dp)
                    .height(
                        if (compact) {
                            27.dp
                        } else {
                            29.dp
                        }
                    )
                    .clickable(
                        actionRunCallback<CompleteTaskAction>(
                            actionParametersOf(
                                TaskIdKey to task.id
                            )
                        )
                    ),
            contentAlignment = Alignment.Center,
        ) {

            Image(
                provider =
                    ImageProvider(
                        R.drawable.ic_widget_checkbox
                    ),
                contentDescription =
                    context.getString(
                        R.string.widget_complete_task,
                        task.title,
                    ),
                modifier =
                    GlanceModifier.size(18.dp),
                colorFilter =
                    ColorFilter.tint(
                        ColorProvider(
                            day = Color(
                                TaskWidgetDataSource.priorityColor(task.priority)
                            ),
                            night = Color(
                                TaskWidgetDataSource.priorityColor(task.priority)
                            ),
                        )
                    ),
            )
        }

        Spacer(
            GlanceModifier.width(2.dp)
        )

        Row(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable(
                        taskAction(
                            context,
                            task.id,
                        )
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            Text(
                text = task.title,
                modifier =
                    GlanceModifier.defaultWeight(),
                maxLines = 1,
                style =
                    TextStyle(
                        color =
                            ColorProvider(
                                day = palette.primaryText,
                                night = palette.primaryText,
                            ),
                        fontSize =
                            (
                                    (
                                            if (compact) {
                                                12f
                                            } else {
                                                13.5f
                                            }
                                            ) * palette.textScale
                                    ).sp,
                        fontWeight = FontWeight.Medium,
                    ),
            )

            if (
                !compact &&
                task.dueLabel.isNotBlank()
            ) {

                Spacer(
                    GlanceModifier.width(5.dp)
                )

                Text(
                    text = task.dueLabel,
                    maxLines = 1,
                    style =
                        TextStyle(
                            color =
                                ColorProvider(
                                    day =
                                        if (task.overdue) {
                                            palette.error
                                        } else {
                                            palette.secondaryText
                                        },
                                    night =
                                        if (task.overdue) {
                                            palette.error
                                        } else {
                                            palette.secondaryText
                                        },
                                ),
                            fontSize =
                                (
                                        10.5f *
                                                palette.textScale
                                        ).sp,
                        ),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    text: String,
    palette: WidgetPalette,
) {

    Box(
        modifier =
            GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {

        Text(
            text = text,
            style =
                TextStyle(
                    color =
                        fixedColorProvider(palette.secondaryText),
                    fontSize =
                        (
                                13f *
                                        palette.textScale
                                ).sp,
                ),
        )
    }
}

private fun configAction(
    context: Context,
    appWidgetId: Int,
    mode: WidgetConfigMode,
) =
    actionStartActivity(
        Intent(
            context,
            TaskWidgetConfigureActivity::class.java,
        )
            .putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                appWidgetId,
            )
            .putExtra(
                TaskWidgetConfigureActivity.EXTRA_MODE,
                mode.name,
            )
            .setData(
                Uri.parse(
                    "pix://widget/config/$appWidgetId/${mode.name}"
                )
            )
    )