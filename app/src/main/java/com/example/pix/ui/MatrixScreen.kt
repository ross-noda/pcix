package com.example.pix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
private val quadrantColors =
    listOf(Color(0xFFFF6D80), Color(0xFFFFCB55), Color(0xFF8199FF), Color(0xFF39CBA9))

@Composable
fun MatrixScreen(
    content: TaskContent,
    today: LocalDate,
    config: MatrixConfig,
    open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit,
    add: (Int) -> Unit,
    retry: () -> Unit,
) {
    if (content.loading) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (content.failed) {
        TextButton(onClick = retry) { Text(stringResource(R.string.retry)) }
        return
    }
    val groups = content.tasks.groupBy { MatrixRules.quadrant(it.task, today, config) }
    val gap = 8.dp
    @Composable
    fun cell(index: Int, modifier: Modifier) {
        Quadrant(index, groups[index].orEmpty(), config, modifier, open, complete, { add(index) })
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val availableWidth = maxWidth
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val headingStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp * LocalTextScale.current, lineHeight = 13.sp * LocalTextScale.current)
    val requiredWidth = quadrantLabels.maxOf { label ->
        with(density) { measurer.measure(AnnotatedString(stringResource(label)), headingStyle, softWrap = false).size.width.toDp() }
    } + (if (config.cornerRadius > 20f) 26.dp else 14.dp)
    val narrowest = (availableWidth - 24.dp) * minOf(config.columnSplit, 1 - config.columnSplit)
    val layout = if (config.layout == 0 && narrowest < requiredWidth) 1 else config.layout
    when (layout) {
        1 ->
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                (0..3).forEach {
                    cell(
                        it,
                        Modifier.weight(if (it < 2) config.rowSplit else 1 - config.rowSplit)
                            .fillMaxWidth(),
                    )
                }
            }
        2 ->
            Row(
                Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                (0..3).forEach {
                    cell(
                        it,
                        Modifier.width(
                                ((availableWidth - 24.dp) * (if (it % 2 == 0) config.columnSplit else 1 - config.columnSplit)).coerceAtLeast(requiredWidth)
                            )
                            .fillMaxHeight(),
                    )
                }
            }
        else ->
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                Row(
                    Modifier.weight(config.rowSplit),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    cell(0, Modifier.weight(config.columnSplit).fillMaxHeight())
                    cell(1, Modifier.weight(1 - config.columnSplit).fillMaxHeight())
                }
                Row(
                    Modifier.weight(1 - config.rowSplit),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    cell(2, Modifier.weight(config.columnSplit).fillMaxHeight())
                    cell(3, Modifier.weight(1 - config.columnSplit).fillMaxHeight())
                }
            }
    }
    }
}

@Composable
private fun Quadrant(
    index: Int,
    tasks: List<TaskWithDetails>,
    config: MatrixConfig,
    modifier: Modifier,
    open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit,
    add: () -> Unit,
) {
    val color = quadrantColors[index]
    val locale = LocalConfiguration.current.locales[0]
    Surface(
        modifier.testTag("quadrant-$index"),
        shape = RoundedCornerShape(config.cornerRadius.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                val titlePadding = if (config.cornerRadius > 20f) 12.dp else 6.dp
                val title = stringResource(quadrantLabels[index])
                val titleStyle = MaterialTheme.typography.labelMedium.copy(fontSize = 10.5f.sp * LocalTextScale.current, lineHeight = 13.sp * LocalTextScale.current)
                BasicText(title,
                    Modifier.fillMaxWidth().padding(start = titlePadding, end = titlePadding, top = 7.dp).testTag("quadrant-title-$index"),
                    style = titleStyle.copy(color = color), maxLines = 1, softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp * LocalTextScale.current,
                        maxFontSize = 10.5f.sp * LocalTextScale.current, stepSize = .1f.sp))
                Text(stringResource(R.string.matrix_count, tasks.size),
                    Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp * LocalTextScale.current, lineHeight = 11.sp * LocalTextScale.current),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box(Modifier.align(Alignment.BottomEnd).size(48.dp).clickable(onClick = add)
                    .testTag("quadrant-add-$index"), contentAlignment = Alignment.BottomEnd) {
                    PixIcon(PixSymbol.PLUS, stringResource(R.string.add_task),
                        modifier = Modifier.padding(6.dp).size(16.dp), tint = color)
                }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 4.dp)) {
                if (tasks.isEmpty())
                    item {
                        Text(
                            stringResource(R.string.matrix_empty),
                            Modifier.padding(6.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5f.sp * LocalTextScale.current),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                items(tasks, key = { it.task.id }) { detail ->
                    val t = detail.task
                    val completedLabel = stringResource(R.string.completed_description, t.title)
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { open(detail) }
                            .testTag("matrix-task-${t.id}"),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(Modifier.size(48.dp).toggleable(value = t.isCompleted, role = Role.Checkbox, onValueChange = { complete(t) })
                            .semantics { contentDescription = completedLabel }, contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(15.dp)) {
                                drawRoundRect(color, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                                if (t.isCompleted) {
                                    drawLine(color, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .43f, size.height * .73f), 1.8.dp.toPx())
                                    drawLine(color, Offset(size.width * .43f, size.height * .73f), Offset(size.width * .82f, size.height * .25f), 1.8.dp.toPx())
                                }
                            }
                        }
                        Column(Modifier.weight(1f).padding(top = 5.dp, end = 6.dp, bottom = 4.dp)) {
                            Text(
                                t.title,
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.5f.sp * com.example.pix.ui.theme.LocalTextScale.current,
                                        lineHeight = 13.sp * com.example.pix.ui.theme.LocalTextScale.current,
                                    ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            t.dueDay?.let {
                                Text(
                                    LocalDate.ofEpochDay(it)
                                        .format(DateTimeFormatter.ofPattern("d MMM", locale)),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp * com.example.pix.ui.theme.LocalTextScale.current,
                                            lineHeight = 11.sp * com.example.pix.ui.theme.LocalTextScale.current,
                                        ),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (t.seriesId != null)
                                PixIcon(
                                    PixSymbol.REPEAT,
                                    stringResource(R.string.recurrence),
                                    modifier = Modifier.size(12.dp),
                                )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixOptions(config: MatrixConfig, save: (MatrixConfig) -> Unit, dismiss: () -> Unit) {
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
