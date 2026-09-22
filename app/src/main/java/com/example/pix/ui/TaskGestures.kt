package com.example.pix.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.*
import java.time.LocalDate
import kotlin.math.roundToInt

val LocalTaskActions = staticCompositionLocalOf<((TaskWithDetails, String) -> Unit)?> { null }

@Composable
fun TaskRow(
    detail: TaskWithDetails,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    dimmed: Boolean = false,
) {
    val actions = LocalTaskActions.current
    var offset by remember(detail.task.id, detail.task.isCompleted) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val reveal = with(LocalDensity.current) { 168.dp.toPx() }.coerceAtMost(width * .65f)
    val complete by rememberUpdatedState(onComplete)
    val latest by rememberUpdatedState(detail)
    Box(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .testTag("task-row-${detail.task.id}")
            .onSizeChanged { width = it.width }
            .pointerInput(detail.task.id, actions != null, detail.task.isCompleted, width) {
                if (actions != null)
                    detectHorizontalDragGestures(
                        onDragCancel = { offset = 0f },
                        onDragEnd = {
                            if (offset >= width * .4f) {
                                offset = 0f
                                complete()
                            } else offset = if (offset < -reveal / 3) -reveal else 0f
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            offset = (offset + amount).coerceIn(-reveal, width.toFloat())
                        },
                    )
            }
    ) {
        if (offset > 0f)
            Row(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixIcon(PixSymbol.CHECK, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(
                        if (detail.task.isCompleted) R.string.reopen_task
                        else R.string.complete_task
                    ),
                    Modifier.padding(start = 8.dp),
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        if (offset < 0f)
            Row(
                Modifier.matchParentSize(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!detail.task.isCompleted)
                    TextButton(
                        onClick = {
                            offset = 0f
                            actions?.invoke(latest, "postpone")
                        }
                    ) {
                        Text(stringResource(R.string.postpone_task))
                    }
                TextButton(
                    onClick = {
                        offset = 0f
                        actions?.invoke(latest, "delete")
                    }
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) {
            TaskRowContent(
                detail,
                { if (offset != 0f) offset = 0f else onOpen() },
                onComplete,
                dimmed,
                if (actions == null) null
                else {
                    {
                        offset = 0f
                        actions(detail, "menu")
                    }
                },
            )
        }
    }
}

data class TaskActionSelection(val detail: TaskWithDetails, val action: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskActionSheet(
    selection: TaskActionSelection,
    lists: List<ListWithCount>,
    model: TasksViewModel,
    dismiss: () -> Unit,
) {
    val detail = selection.detail
    var action by remember(selection) { mutableStateOf(selection.action) }
    var pending by remember(selection) { mutableStateOf<((RecurrenceScope) -> Unit)?>(null) }
    val context = LocalContext.current
    fun scoped(block: (RecurrenceScope) -> Unit) {
        if (detail.task.seriesId == null) {
            block(RecurrenceScope.ONLY_THIS)
            dismiss()
        } else pending = block
    }
    if (pending != null) {
        RecurrenceScopeDialog(
            delete = action == "delete",
            dismiss = { pending = null },
            choose = { scope ->
                pending?.invoke(scope)
                dismiss()
            },
        )
        return
    }
    if (action == "delete") {
        ConfirmDialog(
            stringResource(R.string.delete),
            stringResource(R.string.delete_task_body),
            dismiss,
        ) {
            scoped { model.deleteTask(detail.task.id, it) }
        }
        return
    }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(detail.task.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            when (action) {
                "move" -> {
                    Text(
                        stringResource(R.string.move_to_list),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(lists.size, key = { lists[it].list.id }) { index ->
                            val list = lists[index].list
                            TextButton(
                                onClick = {
                                    scoped { model.moveTask(detail.task.id, list.id, it) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(list.icon + " " + list.name, Modifier.weight(1f))
                                if (detail.task.listId == list.id) PixIcon(PixSymbol.CHECK)
                            }
                        }
                    }
                }
                "postpone" -> {
                    TextButton(
                        onClick = {
                            val (day, minute) =
                                com.example.pix.domain.ReminderRules.snoozed(
                                    java.time.Instant.now(),
                                    java.time.ZoneId.systemDefault(),
                                )
                            scoped { model.postponeTask(detail.task.id, day, minute, it) }
                        }
                    ) {
                        Text(stringResource(R.string.snooze_hour))
                    }
                    TextButton(
                        onClick = {
                            scoped {
                                model.postponeTask(
                                    detail.task.id,
                                    LocalDate.now().plusDays(1).toEpochDay(),
                                    detail.task.minuteOfDay,
                                    it,
                                )
                            }
                        }
                    ) {
                        Text(stringResource(R.string.tomorrow))
                    }
                    TextButton(
                        onClick = {
                            val date =
                                detail.task.dueDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                            DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        scoped {
                                            model.postponeTask(
                                                detail.task.id,
                                                LocalDate.of(year, month + 1, day).toEpochDay(),
                                                detail.task.minuteOfDay,
                                                it,
                                            )
                                        }
                                    },
                                    date.year,
                                    date.monthValue - 1,
                                    date.dayOfMonth,
                                )
                                .show()
                        }
                    ) {
                        Text(stringResource(R.string.choose_date))
                    }
                }
                else -> {
                    TextButton(
                        onClick = {
                            dismiss()
                            model.open(detail)
                        }
                    ) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = { action = "move" }) {
                        Text(stringResource(R.string.move_to_list))
                    }
                    TextButton(
                        onClick = {
                            model.duplicate(detail.task.id)
                            dismiss()
                        }
                    ) {
                        Text(stringResource(R.string.duplicate))
                    }
                    if (!detail.task.isCompleted)
                        TextButton(onClick = { action = "postpone" }) {
                            Text(stringResource(R.string.postpone_task))
                        }
                    TextButton(onClick = { action = "delete" }) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
