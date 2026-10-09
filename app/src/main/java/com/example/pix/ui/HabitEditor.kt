package com.example.pix.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import com.example.pix.ui.theme.ListColors
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitEditor(model: HabitsViewModel, id: String?, done: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val saving by model.saving.collectAsStateWithLifecycle()
    val habits by model.habits.collectAsStateWithLifecycle()
    val rules by model.rules.collectAsStateWithLifecycle()
    val groups by model.groups.collectAsStateWithLifecycle()
    val existing = habits.firstOrNull { it.id == id }
    if (id != null && existing == null) { LinearProgressIndicator(); return }
    val today = LocalDate.now().toEpochDay()
    val initial = remember(id) { existing ?: HabitEntity(name = "") }
    val oldRule = remember(id, rules) { HabitRules.at(rules.filter { it.habitId == id }, today) ?: rules.filter { it.habitId == id }.minByOrNull { it.effectiveDay } }
    if (id != null && oldRule == null) { LinearProgressIndicator(); return }
    var name by rememberSaveable(id) { mutableStateOf(initial.name) }
    var notes by rememberSaveable(id) { mutableStateOf(initial.notes) }
    var pickingIcon by rememberSaveable(id) { mutableStateOf(false) }
    var icon by rememberSaveable(id) { mutableStateOf(initial.icon) }
    var color by rememberSaveable(id) { mutableIntStateOf(initial.color) }
    var group by rememberSaveable(id) { mutableStateOf(initial.groupId) }
    var quantity by rememberSaveable(id) { mutableStateOf(oldRule?.quantity ?: false) }
    var target by rememberSaveable(id) { mutableStateOf((oldRule?.target ?: 1).toString()) }
    var step by rememberSaveable(id) { mutableStateOf((oldRule?.step ?: 1).toString()) }
    var days by rememberSaveable(id) { mutableIntStateOf(oldRule?.weekdays ?: 127) }
    var interval by rememberSaveable(id) { mutableStateOf((oldRule?.intervalDays ?: 1).toString()) }
    var start by rememberSaveable(id) { mutableStateOf(LocalDate.ofEpochDay(oldRule?.startDay ?: today).toString()) }
    var end by rememberSaveable(id) { mutableStateOf(oldRule?.endDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }
    var reminder by rememberSaveable(id) { mutableStateOf(initial.reminderMinute != null) }
    var time by rememberSaveable(id) { mutableStateOf(initial.reminderMinute?.let { "%02d:%02d".format(Locale.ROOT, it / 60, it % 60) } ?: "08:00") }
    var quickGroup by rememberSaveable { mutableStateOf("") }
    val startDay = runCatching { LocalDate.parse(start).toEpochDay() }.getOrNull()
    val endDay = runCatching { LocalDate.parse(end).toEpochDay() }.getOrNull()
    val minute = runCatching { java.time.LocalTime.parse(time).let { it.hour * 60 + it.minute } }.getOrNull()
    val rule = startDay?.let { HabitRuleEntity(habitId = initial.id, effectiveDay = it, startDay = it, endDay = endDay,
        quantity = quantity, target = if (quantity) target.toIntOrNull() ?: 0 else 1, step = if (quantity) step.toIntOrNull() ?: 0 else 1,
        weekdays = days, intervalDays = interval.toIntOrNull() ?: 0) }
    val valid = name.isNotBlank() && name.length <= 200 && rule != null && runCatching { HabitRules.validate(rule) }.isSuccess && (end.isBlank() || endDay != null) && (!reminder || minute != null)
    if (pickingIcon) HabitIconPicker(icon, { icon = it; pickingIcon = false }, { pickingIcon = false })
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.h_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text(stringResource(R.string.h_icon), style = MaterialTheme.typography.titleMedium)
        OutlinedButton({ pickingIcon = true }) {
            HabitIcon(icon, tint = ListColors[color.mod(12)])
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.h_icon_choose))
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) { ListColors.forEachIndexed { index, tint ->
            val description = stringResource(R.string.h_color_number, index + 1)
            IconButton({ color = index }, Modifier.semantics { contentDescription = description; selected = color == index }) { PixIcon(if (color == index) PixSymbol.CHECK else PixSymbol.IMAGE, tint = tint) }
        } }
        Text(stringResource(R.string.h_frequency), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..7).forEach { weekday ->
            val bit = 1 shl (weekday - 1)
            val description = DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, locale)
            FilterChip(days and bit != 0, { days = days xor bit }, label = { Text(DayOfWeek.of(weekday).getDisplayName(TextStyle.NARROW, locale)) }, modifier = Modifier.semantics { contentDescription = description })
        } }
        OutlinedTextField(interval, { interval = it }, label = { Text(stringResource(R.string.h_interval)) }, supportingText = { Text(stringResource(R.string.h_interval_help)) }, modifier = Modifier.fillMaxWidth())
        Text(stringResource(R.string.h_goal), style = MaterialTheme.typography.titleMedium)
        FilterChip(!quantity, { quantity = false }, label = { Text(stringResource(R.string.h_boolean)) })
        FilterChip(quantity, { quantity = true }, label = { Text(stringResource(R.string.h_quantity)) })
        if (quantity) {
            OutlinedTextField(target, { target = it }, label = { Text(stringResource(R.string.h_target)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(step, { step = it }, label = { Text(stringResource(R.string.h_step)) }, modifier = Modifier.fillMaxWidth())
        }
        OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.h_start)) }, supportingText = { Text(stringResource(R.string.h_date_format)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.h_end)) }, supportingText = { Text(stringResource(R.string.h_forever_help)) }, modifier = Modifier.fillMaxWidth())
        if (id != null) Text(stringResource(R.string.h_rule_change), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.h_groups), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(group == null, { group = null }, label = { Text(stringResource(R.string.h_no_group)) })
            groups.forEach { g -> FilterChip(group == g.id, { group = g.id }, label = { Text(g.name) }) }
        }
        OutlinedTextField(quickGroup, { quickGroup = it }, label = { Text(stringResource(R.string.h_group_name)) }, modifier = Modifier.fillMaxWidth())
        TextButton({ val g = HabitGroupEntity(name = quickGroup); model.saveGroup(g); quickGroup = "" }, enabled = quickGroup.isNotBlank()) { Text(stringResource(R.string.h_add_group)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.reminders), Modifier.weight(1f))
            Switch(reminder, { reminder = it }, modifier = Modifier.semantics { contentDescription = name })
        }
        if (reminder) {
            OutlinedTextField(time, { time = it }, label = { Text(stringResource(R.string.h_time)) }, modifier = Modifier.fillMaxWidth())
            ReminderControls(showSwitch = false)
        }
        OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.h_notes)) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Button({ model.save(initial.copy(name = name, notes = notes, icon = icon, color = color, groupId = group, reminderMinute = if (reminder) minute else null), requireNotNull(rule)) { done() } }, enabled = valid && !saving, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
        if (!valid) Text(stringResource(R.string.h_validation), style = MaterialTheme.typography.bodySmall)
    }
}
