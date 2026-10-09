package com.example.pix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import com.example.pix.ui.theme.LocalTextScale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val quadrantLabels =
    listOf(
        R.string.quadrant_do,
        R.string.quadrant_plan,
        R.string.quadrant_delegate,
        R.string.quadrant_eliminate,
    )
internal val quadrantColors =
    listOf(Color(0xFFEF5964), Color(0xFFF1BE37), Color(0xFF607DE2), Color(0xFF28C6A3))

@Composable
fun MatrixScreen(
    content: TaskContent,
    today: LocalDate,
    config: MatrixConfig,
    open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit,
    add: (Int) -> Unit,
    retry: () -> Unit,
    configure: (MatrixConfig) -> Unit = {},
) {
    if (content.loading) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    if (content.failed) { TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }; return }
    val groups = MatrixRules.groups(content.tasks, today, config)
    val order = MatrixRules.orderedIds(config)
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth
            val scale = LocalDensity.current.fontScale * LocalTextScale.current
            val gridHeight = maxHeight.coerceAtLeast(480.dp * scale)
            @Composable fun cell(index: Int, modifier: Modifier, bounded: Boolean) {
                Quadrant(order[index], groups[order[index]].orEmpty(), today, config, modifier, bounded, open, complete) { add(index) }
            }
            when {
                config.layout == 2 -> Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { cell(it, Modifier.width((availableWidth * (if (it % 2 == 0) config.columnSplit else 1 - config.columnSplit)).coerceAtLeast(280.dp * scale)).fillMaxHeight(), true) }
                }
                config.layout == 0 -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(Modifier.fillMaxWidth().height(gridHeight).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) { row -> Row(Modifier.weight(if (row == 0) config.rowSplit else 1 - config.rowSplit), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        cell(row * 2, Modifier.weight(config.columnSplit).fillMaxHeight(), true)
                        cell(row * 2 + 1, Modifier.weight(1 - config.columnSplit).fillMaxHeight(), true)
                    } }
                }
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { index -> item(key = index) { cell(index, Modifier.fillMaxWidth(), false) } }
                }
            }
        }
    }
}

@Composable
private fun Quadrant(
    index: Int, tasks: List<TaskWithDetails>, today: LocalDate, config: MatrixConfig,
    modifier: Modifier, bounded: Boolean, open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit, add: () -> Unit,
) {
    val color = quadrantColors[index]
    val scale = LocalTextScale.current
    val locale = LocalConfiguration.current.locales[0]
    val numerals = listOf("I", "II", "III", "IV")
    // Keep the same neutral surface in dark mode as the supplied reference.
    val dark = MaterialTheme.colorScheme.background.luminance() < .3f
    val surface = if (dark) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surfaceContainer
    Surface(modifier.testTag("quadrant-$index"), shape = RoundedCornerShape(config.cornerRadius.dp), color = surface) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(17.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                    Text(numerals[index], color = surface, fontSize = 12.sp, lineHeight = 14.sp)
                }
                Spacer(Modifier.width(4.dp))
                Text(matrixTitle(config, index), Modifier.weight(1f).testTag("quadrant-title-$index"),
                    color = color, fontSize = 12.sp * scale, lineHeight = 16.sp * scale,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            @Composable fun taskRow(detail: TaskWithDetails) {
                val task = detail.task
                val checkboxColor = if (index == 3) MaterialTheme.colorScheme.outline else color
                val completedLabel = stringResource(R.string.completed_description, task.title)
                Row(Modifier.fillMaxWidth().heightIn(min = 38.dp)
                    .clickable { open(detail) }.semantics {
                        customActions = listOf(CustomAccessibilityAction(completedLabel) { complete(task); true })
                    }.testTag("matrix-task-${task.id}")
                    .padding(start = 8.dp, end = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.width(25.dp).height(28.dp)
                        .toggleable(task.isCompleted, role = Role.Checkbox, onValueChange = { complete(task) })
                        .semantics { contentDescription = completedLabel }, contentAlignment = Alignment.TopCenter) {
                        Canvas(Modifier.padding(top = 2.dp).size(16.dp)) {
                            drawRoundRect(checkboxColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(1.4.dp.toPx()))
                            if (task.isCompleted) {
                                drawLine(checkboxColor, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .43f, size.height * .73f), 1.8.dp.toPx())
                                drawLine(checkboxColor, Offset(size.width * .43f, size.height * .73f), Offset(size.width * .82f, size.height * .25f), 1.8.dp.toPx())
                            }
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(task.title, fontSize = 14.sp * scale, lineHeight = 17.sp * scale, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        TaskTiming.lastDay(task)?.let { day ->
                            val date = LocalDate.ofEpochDay(day)
                            Text(date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", locale)),
                                fontSize = 10.sp * scale, lineHeight = 13.sp * scale,
                                color = if (date < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("matrix-date-${task.id}"))
                        }
                    }
                }
            }
            if (bounded) LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(tasks, key = { it.task.id }) { taskRow(it) }
            } else tasks.forEach { key(it.task.id) { taskRow(it) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixOptions(config: MatrixConfig, save: (MatrixConfig) -> Unit, dismiss: () -> Unit, editCards: () -> Unit = {}) {
    var draft by remember { mutableStateOf(config) }
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.matrix_options),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedButton(onClick = { save(draft); editCards() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.matrix_edit_cards)) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.matrix_hide_children), Modifier.weight(1f))
                Switch(draft.hideChildren, { draft = draft.copy(hideChildren = it) }, Modifier.testTag("matrix-hide-children"))
            }
            Text(stringResource(R.string.matrix_layout))
            ChoiceRow(
                listOf(
                    "0" to stringResource(R.string.matrix_grid),
                    "1" to stringResource(R.string.matrix_rows),
                    "2" to stringResource(R.string.matrix_columns),
                ),
                draft.layout.toString(),
            ) {
                draft = draft.copy(layout = it.toInt())
            }
            Text(stringResource(R.string.matrix_width))
            Slider(
                draft.columnSplit,
                { draft = draft.copy(columnSplit = it) },
                valueRange = 0.3f..0.7f,
                modifier = Modifier.testTag("matrix-width"),
            )
            Text(stringResource(R.string.matrix_height))
            Slider(draft.rowSplit, { draft = draft.copy(rowSplit = it) }, valueRange = 0.3f..0.7f)
            Text(stringResource(R.string.matrix_corners))
            Slider(
                draft.cornerRadius,
                { draft = draft.copy(cornerRadius = it) },
                valueRange = 0f..32f,
            )
            Text(stringResource(R.string.matrix_urgent_days, draft.urgentDays))
            Slider(
                draft.urgentDays.toFloat(),
                { draft = draft.copy(urgentDays = it.toInt()) },
                valueRange = 0f..30f,
                steps = 29,
            )
            Text(stringResource(R.string.matrix_important_threshold))
            ChoiceRow(
                listOf(
                    "1" to stringResource(R.string.low),
                    "3" to stringResource(R.string.medium),
                    "5" to stringResource(R.string.high),
                ),
                draft.importantPriority.toString(),
            ) {
                draft = draft.copy(importantPriority = it.toInt())
            }
            Text(
                stringResource(R.string.matrix_rules_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { draft = MatrixConfig() }) {
                    Text(stringResource(R.string.reset))
                }
                Button(
                    onClick = {
                        save(draft)
                        dismiss()
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
fun MatrixTaskFields(task: TaskEntity, edit: (TaskEntity) -> Unit) {
    Text(stringResource(R.string.matrix), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.matrix_task_hint), style = MaterialTheme.typography.bodySmall)
    val choices =
        listOf(
            "auto" to stringResource(R.string.automatic),
            "yes" to stringResource(R.string.yes),
            "no" to stringResource(R.string.no),
        )
    fun key(v: Boolean?) =
        when (v) {
            true -> "yes"
            false -> "no"
            null -> "auto"
        }
    fun value(v: String) =
        when (v) {
            "yes" -> true
            "no" -> false
            else -> null
        }
    Text(stringResource(R.string.urgent))
    ChoiceRow(choices, key(task.matrixUrgent)) { edit(task.copy(matrixUrgent = value(it))) }
    Text(stringResource(R.string.important))
    ChoiceRow(choices, key(task.matrixImportant)) { edit(task.copy(matrixImportant = value(it))) }
}
