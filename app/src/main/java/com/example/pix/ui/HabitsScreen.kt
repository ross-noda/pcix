package com.example.pix.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import com.example.pix.ui.theme.ListColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitsScreen(model: HabitsViewModel, create: () -> Unit, open: (String) -> Unit, manage: () -> Unit) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val habits by model.habits.collectAsStateWithLifecycle()
    val rules by model.rules.collectAsStateWithLifecycle()
    val logs by model.logs.collectAsStateWithLifecycle()
    val groups by model.groups.collectAsStateWithLifecycle()
    val selectedGroup by model.group.collectAsStateWithLifecycle()
    val day by model.date.collectAsStateWithLifecycle()
    val today by model.today.collectAsStateWithLifecycle()
    var archived by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.ofEpochDay(day)
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    val dayRules = remember(rules, day) { rules.groupBy { it.habitId }.mapValues { HabitRules.at(it.value, day) } }
    val dayLogs = remember(logs, day) { logs.filter { it.day == day }.associateBy { it.habitId } }
    val shown = remember(habits, dayRules, dayLogs, day, selectedGroup, archived) {
        val filtered = habits.filter { h ->
            h.active != archived && (selectedGroup == null || h.groupId == selectedGroup) &&
                (archived || dayLogs[h.id]?.sourceStatus != null || dayRules[h.id]?.let { HabitRules.scheduled(it, day) } == true)
        }
        HabitRules.homeOrder(filtered, dayRules, dayLogs)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ model.selectDate(day - 7) }) { PixIcon(PixSymbol.BACK, stringResource(R.string.h_previous_week)) }
                    Text(date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton({ model.selectDate(today) }) { Text(stringResource(R.string.today)) }
                    IconButton({ model.selectDate(day + 7) }) { PixIcon(PixSymbol.CHEVRON, stringResource(R.string.h_next_week)) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    repeat(7) { index ->
                        val d = monday.plusDays(index.toLong()); val value = d.toEpochDay()
                        Surface(onClick = { model.selectDate(value) }, modifier = Modifier.weight(1f).semantics { selected = value == day; contentDescription = d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)) }, shape = MaterialTheme.shapes.medium,
                            color = if (value == day) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium)
                                Text(d.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                                if (value == today) Box(Modifier.size(5.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            }
                        }
                    }
                }
            }
            item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selectedGroup == null, { model.selectGroup(null) }, label = { Text(stringResource(R.string.all)) })
                groups.forEach { g -> FilterChip(selectedGroup == g.id, { model.selectGroup(g.id) }, label = { Text(g.name) }) }
            } }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(manage) { Text(stringResource(R.string.h_manage_groups)) }
                FilterChip(archived, { archived = !archived }, label = { Text(stringResource(R.string.h_archived)) })
            } }
            if (shown.isEmpty()) item { Text(stringResource(R.string.h_empty), Modifier.padding(24.dp)) }
            items(shown, key = { it.id }) { habit ->
                val rule = dayRules[habit.id]
                val log = dayLogs[habit.id]
                val completedCheck = HabitRules.completedCheck(rule, log)
                Card(onClick = { open(habit.id) }, modifier = Modifier.fillMaxWidth(),
                    colors = if (completedCheck) CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) else CardDefaults.cardColors()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HabitIcon(habit.icon, tint = if (completedCheck) MaterialTheme.colorScheme.onSurfaceVariant else ListColors[habit.color.mod(12)])
                            Text(habit.name, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.titleMedium)
                        }
                        if (rule != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (log?.skipped == true) stringResource(R.string.h_skipped) else if (log?.sourceStatus != null) stringResource(R.string.h_csv_recorded_count, log.count, habit.unit) else stringResource(R.string.h_progress, log?.count ?: 0, rule.target), Modifier.weight(1f))
                            if (rule.quantity) {
                                val label = stringResource(R.string.h_decrement, habit.name)
                                TextButton({ model.record(habit.id, day, delta = -rule.step) }, enabled = day <= today && habit.active && (log?.count ?: 0) > 0, modifier = Modifier.semantics { contentDescription = label }) { Text(stringResource(R.string.h_minus, rule.step)) }
                            }
                            val label = stringResource(if (rule.quantity) R.string.h_increment else R.string.h_toggle, habit.name)
                            FilledTonalButton({ model.record(habit.id, day) }, enabled = day <= today && habit.active, modifier = Modifier.semantics { contentDescription = label },
                                colors = if (completedCheck) ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ) else ButtonDefaults.filledTonalButtonColors()) {
                                if (HabitRules.complete(rule, log)) PixIcon(PixSymbol.CHECK)
                                Text(if (rule.quantity) stringResource(R.string.h_plus, rule.step) else stringResource(if (HabitRules.complete(rule, log)) R.string.undo else R.string.h_done))
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(create, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { PixIcon(PixSymbol.PLUS, stringResource(R.string.h_new)) }
    }
}

fun habitSymbol(raw: String) = runCatching { PixSymbol.valueOf(raw) }.getOrDefault(PixSymbol.REPEAT)

@Composable
fun HabitGroupsScreen(model: HabitsViewModel) {
    val groups by model.groups.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var edit by remember { mutableStateOf<HabitGroupEntity?>(null) }
    var deleting by remember { mutableStateOf<HabitGroupEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.h_group_name)) }, modifier = Modifier.fillMaxWidth())
            Button({ model.saveGroup(edit?.copy(name = name) ?: HabitGroupEntity(name = name, sortOrder = groups.size.toLong())); name = ""; edit = null }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        }
        items(groups, key = { it.id }) { group -> Card { Column(Modifier.padding(16.dp)) {
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton({ edit = group; name = group.name }) { Text(stringResource(R.string.h_rename)) }
                TextButton({ deleting = group }) { Text(stringResource(R.string.delete)) }
            }
        } } }
    }
    deleting?.let { g -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text(stringResource(R.string.delete)) }, text = { Text(stringResource(R.string.h_delete_group)) },
        confirmButton = { TextButton({ model.deleteGroup(g.id); deleting = null }) { Text(stringResource(R.string.delete)) } }, dismissButton = { TextButton({ deleting = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
fun HabitOrderScreen(model: HabitsViewModel) {
    val habits by model.habits.collectAsStateWithLifecycle()
    val active = habits.filter { it.active }
    val candidates = active.map { it.id }
    val state = remember { ReorderState() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.h_reorder_help)) }
        if (active.isEmpty()) item { Text(stringResource(R.string.h_empty)) }
        items(active, key = { it.id }) { habit ->
            ReorderItem(habit.id, candidates, state, model::reorder) {
                ListItem(headlineContent = { Text(habit.name) }, leadingContent = {
                    HabitIcon(habit.icon, tint = ListColors[habit.color.mod(12)])
                })
            }
        }
    }
}
