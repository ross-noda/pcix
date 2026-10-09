package com.example.pix.widget

import android.appwidget.AppWidgetManager
import android.content.*
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.room.withTransaction
import com.example.pix.R
import com.example.pix.MainActivity
import com.example.pix.PixApplication
import com.example.pix.cloud.AccountSessionState
import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import com.example.pix.ui.LucideCatalog
import com.example.pix.ui.theme.ListColors
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

internal data class HabitWidgetRow(val habit: HabitEntity, val rule: HabitRuleEntity, val log: HabitLogEntity?)
internal fun habitWidgetRows(habits: List<HabitEntity>, rules: List<HabitRuleEntity>, logs: List<HabitLogEntity>, day: Long): List<HabitWidgetRow> {
    val currentRules = rules.groupBy { it.habitId }.mapValues { HabitRules.at(it.value, day) }
    val currentLogs = logs.filter { it.day == day }.associateBy { it.habitId }
    val active = habits.filter { it.active && currentRules[it.id]?.let { r -> HabitRules.scheduled(r, day) } == true }
        .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }, { it.id }))
    return HabitRules.homeOrder(active, currentRules, currentLogs).map { HabitWidgetRow(it, requireNotNull(currentRules[it.id]), currentLogs[it.id]) }
}

internal suspend fun widgetAccountReady(app: PixApplication): Boolean {
    if (!app.cloud.configured) return true
    val state = withTimeoutOrNull(5000) { app.session.state.first { it !is AccountSessionState.Restoring && it !is AccountSessionState.PreparingAccount } }
    return state is AccountSessionState.Ready && app.accounts.owner() == state.user.id
}

class HabitWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as PixApplication
        val ready = widgetAccountReady(app)
        val owner = app.accounts.owner().orEmpty()
        val day = LocalDate.now().toEpochDay()
        val rows = if (ready) app.database.withTransaction {
            val dao = app.database.habitDao()
            habitWidgetRows(dao.habits(), dao.rules(), dao.logs(), day)
        } else emptyList()
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        provideContent {
            Box(GlanceModifier.fillMaxSize().background(widgetBackground(context, widgetId)).cornerRadius(24.dp).appWidgetBackground().padding(8.dp)) {
                if (rows.isEmpty()) {
                    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_PLANNER).putExtra("destination", "habits").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))), contentAlignment = Alignment.Center) {
                        Text(context.getString(if (ready) R.string.h_widget_empty else R.string.h_widget_login), style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp))
                    }
                } else LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(rows.chunked(2)) { pair ->
                        Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            HabitTile(context, pair[0], widgetId, owner, day, GlanceModifier.defaultWeight())
                            Spacer(GlanceModifier.width(6.dp))
                            if (pair.size == 2) HabitTile(context, pair[1], widgetId, owner, day, GlanceModifier.defaultWeight())
                            else Spacer(GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitTile(context: Context, row: HabitWidgetRow, widgetId: Int, owner: String, day: Long, modifier: GlanceModifier) {
    val scale = widgetContentScale(LocalSize.current.width.value, LocalSize.current.height.value, 180f)
    val tileHeight = 64f * scale
    val h = row.habit
    val accent = ListColors[h.color.mod(ListColors.size)]
    val fraction = habitWidgetProgress(row.rule, row.log)
    val percent = java.text.NumberFormat.getPercentInstance(context.resources.configuration.locales[0]).format(fraction.toDouble())
    val progress = context.getString(R.string.h_widget_progress_percent, row.log?.count ?: 0, row.rule.target, percent)
    val iconColor = if (fraction >= 1f) Color(0xFFAAAAAA) else accent
    val tileWidth = ((LocalSize.current.width.value - 22f) / 2f).coerceAtLeast(1f)
    val density = context.resources.displayMetrics.density
    val backdrop = remember(tileWidth, tileHeight, density, fraction) { habitTileBackground(tileWidth, tileHeight, density, fraction) }
    val drawable = LucideCatalog.byId[h.icon]?.drawable ?: when(h.icon) {
        "CHECK" -> R.drawable.lucide_check
        "CHECKLIST" -> R.drawable.lucide_list_checks
        "FLAG" -> R.drawable.lucide_flag
        "CLOCK" -> R.drawable.lucide_clock
        "IMAGE" -> R.drawable.lucide_image
        "CALENDAR" -> R.drawable.lucide_calendar
        else -> R.drawable.lucide_repeat
    }
    Row(modifier.height(tileHeight.dp).background(ImageProvider(backdrop), contentScale = ContentScale.FillBounds)
        .cornerRadius((18 * scale).dp).clickable(actionSendBroadcast(habitTapIntent(context, widgetId, h.id, owner, day))).padding((10 * scale).dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.size((36 * scale).dp).background(iconColor.copy(alpha = .22f)).cornerRadius((18 * scale).dp), contentAlignment = Alignment.Center) {
            Image(ImageProvider(drawable), context.getString(R.string.h_widget_complete, h.name), GlanceModifier.size((24 * scale).dp), colorFilter = ColorFilter.tint(ColorProvider(iconColor)))
        }
        Spacer(GlanceModifier.width((8 * scale).dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(h.name, style = TextStyle(color = ColorProvider(if (fraction >= 1f) Color(0xFFBBBBBB) else Color(0xFFF0F0F0)), fontSize = (14 * scale).sp, fontWeight = FontWeight.Medium), maxLines = 2)
            Text(progress, style = TextStyle(color = ColorProvider(Color(0xFFBDBDBD)), fontSize = (12 * scale).sp), maxLines = 1)
        }
    }
}

internal fun habitTapIntent(context: Context, widget: Int, habit: String, owner: String, day: Long) =
    Intent(context, HabitWidgetTapReceiver::class.java).setAction(HabitWidgetTapReceiver.COMPLETE)
        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        .setData(Uri.Builder().scheme("pix").authority("habit-widget").appendPath(widget.toString()).appendPath(owner).appendPath(day.toString()).appendPath(habit).build())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget).putExtra("habit", habit).putExtra("owner", owner).putExtra("day", day)

/** Shared with tests; imported statuses retain precedence over a guessed CSV target. */
internal fun habitWidgetProgress(rule: HabitRuleEntity, log: HabitLogEntity?): Float = when {
    log == null || log.skipped -> 0f
    HabitRules.complete(rule, log) -> 1f
    log.sourceStatus == "Failed" || log.sourceStatus == "" -> 0f
    else -> ((log.count.toFloat() / rule.target.coerceAtLeast(1))).coerceIn(0f, .99f)
}

private fun habitTileBackground(widthDp: Float, heightDp: Float, density: Float, progress: Float): android.graphics.Bitmap {
    val width = (widthDp * density).toInt().coerceAtLeast(1)
    val height = (heightDp * density).toInt().coerceAtLeast(1)
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val bounds = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
    val outline = android.graphics.Path().apply { addRoundRect(bounds, 18 * density, 18 * density, android.graphics.Path.Direction.CW) }
    canvas.clipPath(outline)
    canvas.drawColor(android.graphics.Color.rgb(51, 51, 51))
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(74, 74, 74) }
    canvas.drawRect(0f, 0f, width * progress, height.toFloat(), paint)
    return bitmap
}
