package com.example.pix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.TaskWithDetails
import com.example.pix.ui.theme.ListColors
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

val priorityLabels = listOf(R.string.none, R.string.low, R.string.medium, R.string.high)
val priorityValues = listOf(0, 1, 3, 5)

@Composable
fun TaskRowContent(
    detail: TaskWithDetails,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    dimmed: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val t = detail.task
    val locale = LocalConfiguration.current.locales[0]
    val completeDescription = stringResource(R.string.completed_description, t.title)
    val priorityDescription =
        stringResource(R.string.priority) +
            ": " +
            stringResource(priorityLabels[priorityValues.indexOf(t.priority).coerceAtLeast(0)])
    val subdued = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier =
            Modifier.then(
                if (dimmed)
                    Modifier.alpha(.65f).drawWithContent {
                        val paint =
                            Paint().apply {
                                colorFilter =
                                    ColorFilter.colorMatrix(
                                        ColorMatrix().apply { setToSaturation(.12f) }
                                    )
                            }
                        drawContext.canvas.saveLayer(Rect(Offset.Zero, size), paint)
                        drawContent()
                        drawContext.canvas.restore()
                    }
                else Modifier
            ),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 50.dp)
                .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
                .padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (detail.subtasks.isNotEmpty())
                IconButton(
                    onClick = onComplete,
                    modifier =
                        Modifier.semantics {
                            contentDescription = completeDescription
                            stateDescription = priorityDescription
                        },
                ) {
                    PixIcon(
                        if (t.isCompleted) PixSymbol.TASKS else PixSymbol.SUBTASK,
                        tint = if (t.isCompleted) subdued else priorityColor(t.priority),
                    )
                }
            else
                Checkbox(
                    checked = t.isCompleted,
                    onCheckedChange = { onComplete() },
                    modifier =
                        Modifier.semantics {
                            contentDescription = completeDescription
                            stateDescription = priorityDescription
                        },
                    colors =
                        CheckboxDefaults.colors(
                            uncheckedColor = priorityColor(t.priority),
                            checkedColor = subdued.copy(alpha = .35f),
                        ),
                )
            Column(
                Modifier.weight(1f).padding(vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (t.isCompleted) subdued else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (t.isCompleted) TextDecoration.LineThrough else null,
                )
                if (t.durationMinutes != null)
                    Text(
                        durationEndLabel(t),
                        style = MaterialTheme.typography.labelSmall,
                        color = subdued,
                    )
                if (
                    detail.tags.isNotEmpty() ||
                        detail.list.id != com.example.pix.data.INBOX_ID ||
                        detail.subtasks.isNotEmpty()
                )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            Modifier.size(5.dp)
                                .background(ListColors[detail.list.color.mod(12)], CircleShape)
                        )
                        Text(
                            listOf(
                                    detail.list.name,
                                    detail.tags.take(2).joinToString(" ") { "#${it.name}" },
                                    if (detail.subtasks.isNotEmpty())
                                        "${detail.subtasks.count{it.isCompleted}}/${detail.subtasks.size}"
                                    else "",
                                )
                                .filter { it.isNotEmpty() }
                                .joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = subdued,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
            }
            Column(
                Modifier.padding(start = 10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                t.dueDay?.let {
                    Text(
                        LocalDate.ofEpochDay(it)
                            .format(DateTimeFormatter.ofPattern("d MMM", locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (t.isCompleted) subdued else MaterialTheme.colorScheme.primary,
                    )
                }
                t.minuteOfDay?.let {
                    Text(
                        LocalTime.of(it / 60, it % 60)
                            .format(DateTimeFormatter.ofPattern("HH:mm", locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = subdued,
                    )
                }
                if (t.seriesId != null)
                    PixIcon(
                        PixSymbol.REPEAT,
                        stringResource(R.string.recurrence),
                        Modifier.size(14.dp),
                        subdued,
                    )
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun priorityColor(priority: Int): Color =
    when (priority) {
        5 -> ListColors[1]
        3 -> ListColors[0]
        1 -> com.example.pix.ui.theme.AccentBlue
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
