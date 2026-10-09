package com.example.pix.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.example.pix.ui.theme.ListColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitDetail(model: HabitsViewModel, id: String, edit: () -> Unit, back: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    LaunchedEffect(id) { model.selectHabit(id) }
    val habits by model.habits.collectAsStateWithLifecycle()
    val allRules by model.rules.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val groups by model.groups.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    val habit = habits.firstOrNull { it.id == id } ?: return
    val accent = ListColors[habit.color.mod(ListColors.size)]
    val completedColor = lerp(accent, Color.Black, .28f)
    val partialColor = lerp(accent, Color.White, .45f)
    val rules = remember(allRules, id) { allRules.filter { it.habitId == id } }
    val logs = remember(history, id) { history.filter { it.habitId == id } }
    var monthRaw by rememberSaveable(id) { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(today)).toString()) }
    val month = YearMonth.parse(monthRaw)
    var menu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var move by remember { mutableStateOf(false) }
    var editingDay by rememberSaveable(id) { mutableStateOf<Long?>(null) }
    val stats = remember(rules, logs, today) { HabitRules.stats(rules, logs, today) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            HabitIcon(habit.icon, tint = accent)
            Text(habit.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
            Box { IconButton({ menu = true }) { PixIcon(PixSymbol.MORE, stringResource(R.string.h_actions)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.h_edit)) }, { menu = false; edit() })
                    DropdownMenuItem({ Text(stringResource(R.string.h_move)) }, { menu = false; move = true })
                    DropdownMenuItem({ Text(stringResource(if (habit.active) R.string.h_archive else R.string.h_activate)) }, { menu = false; model.update(habit.copy(active = !habit.active)) })
                    DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; delete = true })
                }
            }
        } }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatLine(R.string.h_completed_days, stats.completed.toString())
            StatLine(R.string.h_failed_days, stats.failed.toString())
            StatLine(R.string.h_skipped_days, stats.skipped.toString())
            StatLine(R.string.h_total_units, stats.total.toString())
        } } }
        item { Card { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ monthRaw = month.minusMonths(1).toString() }) { PixIcon(PixSymbol.BACK, stringResource(R.string.h_previous_month)) }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                IconButton({ monthRaw = month.plusMonths(1).toString() }) { PixIcon(PixSymbol.CHEVRON, stringResource(R.string.h_next_month)) }
            }
            Row { (1..7).forEach { Text(DayOfWeek.of(it).getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
            val first = month.atDay(1).minusDays((month.atDay(1).dayOfWeek.value - 1).toLong())
            repeat(6) { week -> Row(Modifier.fillMaxWidth()) { repeat(7) { index ->
                val d = first.plusDays((week * 7 + index).toLong()); val day = d.toEpochDay()
                val rule = HabitRules.at(rules, day); val log = logs.firstOrNull { it.day == day }
                val scheduled = rule?.let { HabitRules.scheduled(it, day) } == true
                val state = when { log?.sourceStatus == "Failed" -> R.string.h_failed; log?.sourceStatus == "Inprogress" -> R.string.h_partial; log?.sourceStatus == "" -> R.string.h_csv_unknown; log?.sourceStatus == "Completed" -> R.string.h_done; day > today -> R.string.h_future; log?.skipped == true -> R.string.h_skipped; rule != null && HabitRules.complete(rule, log) -> R.string.h_done; (log?.count ?: 0) > 0 -> R.string.h_partial; !scheduled -> R.string.h_not_scheduled; day == today -> R.string.h_pending; else -> R.string.h_failed }
                val label = d.toString() + ": " + stringResource(state) + if (log != null) " (${log.count})" else ""
                val inMonth = d.month == month.month
                val color = if (!inMonth || day > today) Color.Transparent else when (state) {
                    R.string.h_done -> completedColor
                    R.string.h_partial -> partialColor
                    else -> Color.Transparent
                }
                val foreground = if (color == Color.Transparent) MaterialTheme.colorScheme.onSurface else if (color.luminance() > .179f) Color.Black else Color.White
                Surface(onClick = { editingDay = day }, enabled = inMonth && day <= today && habit.active && (scheduled || log != null), modifier = Modifier.weight(1f).padding(2.dp).heightIn(min = 48.dp).semantics { contentDescription = label }, color = color, contentColor = foreground, shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                        Text(if (inMonth) d.dayOfMonth.toString() else "", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(completedColor to R.string.h_done, partialColor to R.string.h_partial, Color.Transparent to R.string.h_calendar_none).forEach { (tint, label) ->
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(10.dp).background(tint, androidx.compose.foundation.shape.CircleShape).border(1.dp, MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape))
                        Text(stringResource(label), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } } }
        item { HabitCharts(logs, today, habit.unit, accent) }
        item { Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatLine(R.string.h_current_streak, stats.current.toString())
            StatLine(R.string.h_best_streak, stats.best.toString())
            stats.series.forEach { series ->
                val format = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
                Text(stringResource(R.string.h_series_range, LocalDate.ofEpochDay(series.start).format(format), LocalDate.ofEpochDay(series.end).format(format), series.length), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { series.length.toFloat() / stats.best.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(10.dp), color = accent, trackColor = accent.copy(alpha = .15f))
            }
        } } }
        if (habit.notes.isNotBlank()) item { Text(habit.notes) }
    }
    if (delete) AlertDialog(onDismissRequest = { delete = false }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.h_delete_warning)) }, confirmButton = { TextButton({ model.delete(id, back); delete = false }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton({ delete = false }) { Text(stringResource(R.string.cancel)) } })
    if (move) AlertDialog(onDismissRequest = { move = false }, title = { Text(stringResource(R.string.h_move)) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
        TextButton({ model.update(habit.copy(groupId = null)); move = false }) { Text(stringResource(R.string.h_no_group)) }
        groups.forEach { g -> TextButton({ model.update(habit.copy(groupId = g.id)); move = false }) { Text(g.name) } }
    } }, confirmButton = { TextButton({ move = false }) { Text(stringResource(R.string.close)) } })
    editingDay?.let { day -> HabitLogDialog(habit, day, logs.firstOrNull { it.day == day }, (HabitRules.at(rules, day)?.quantity == true || logs.any { it.day == day && it.sourceStatus != null }), { count, skipped -> model.record(id, day, count, skipped); editingDay = null }, { editingDay = null }) }
}

@Composable private fun StatLine(label: Int, value: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(stringResource(label), Modifier.weight(1f)); Text(value, style = MaterialTheme.typography.titleMedium) } }

@Composable private fun HabitLogDialog(habit: HabitEntity, day: Long, log: HabitLogEntity?, quantity: Boolean, save: (Int, Boolean) -> Unit, dismiss: () -> Unit) {
    var count by rememberSaveable(day) { mutableStateOf((log?.count ?: 0).toString()) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(habit.name + " · " + LocalDate.ofEpochDay(day)) }, text = { Column {
        OutlinedTextField(count, { count = it }, label = { Text(stringResource(R.string.h_count)) })
        TextButton({ save(0, true) }) { Text(stringResource(R.string.h_skip)) }
        TextButton({ save(0, false) }) { Text(stringResource(R.string.h_reset)) }
    } }, confirmButton = { TextButton({ save(count.toInt(), false) }, enabled = count.toIntOrNull()?.let { it in 0..(if (quantity) 1000000 else 1) } == true) { Text(stringResource(R.string.save)) } }, dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel)) } })
}
