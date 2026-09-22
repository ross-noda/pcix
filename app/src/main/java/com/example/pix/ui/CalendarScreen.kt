package com.example.pix.ui

import com.example.pix.domain.CalendarRules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.ui.theme.ListColors
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

@Composable
fun CalendarScreen(
    selectedDay: Long,
    today: LocalDate,
    marks: List<CalendarMark>,
    content: TaskContent,
    select: (Long) -> Unit,
    open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit,
    googleEvents: List<GoogleEventEntity> = emptyList(),
    openGoogle: (GoogleEventEntity) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val selected = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.from(selected)
    val days = remember(month) { CalendarRules.days(month) }
    val previousLabel = stringResource(R.string.previous_month)
    val nextLabel = stringResource(R.string.next_month)
    val byDay = remember(marks, googleEvents) {
        val task = marks.groupBy { it.dueDay }
        val extra =
            googleEvents
                .flatMap { event -> (event.startDay until event.endDay).map { it to event } }
                .groupBy({ it.first }, { it.second })
        task.keys.union(extra.keys).associateWith { day ->
            DayDots(task[day].orEmpty(), extra[day].orEmpty())
        }
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { select(CalendarRules.moveSelection(selected, -1).toEpochDay()) },
                    modifier =
                        Modifier.testTag("previous-month").semantics {
                            contentDescription = previousLabel
                        },
                ) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = { select(CalendarRules.moveSelection(selected, 1).toEpochDay()) },
                    modifier =
                        Modifier.testTag("next-month").semantics { contentDescription = nextLabel },
                ) {
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }
            TextButton(
                onClick = { select(today.toEpochDay()) },
                modifier = Modifier.padding(horizontal = 8.dp).testTag("calendar-today"),
            ) {
                Text(stringResource(R.string.go_today))
            }
        }
        item {
            var drag by remember { mutableFloatStateOf(0f) }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).pointerInput(month) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = {
                            if (kotlin.math.abs(drag) > 64.dp.toPx())
                                select(
                                    CalendarRules.moveSelection(selected, if (drag < 0) 1 else -1)
                                        .toEpochDay()
                                )
                        },
                    ) { change, amount ->
                        change.consume()
                        drag += amount
                    }
                }
            ) {
                Row {
                    DayOfWeek.entries.forEach { day ->
                        Box(
                            Modifier.weight(1f).height(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.getDisplayName(TextStyle.NARROW, locale),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
                days.chunked(7).forEach { week ->
                    Row {
                        week.forEach { date ->
                            val entries = byDay[date.toEpochDay()]
                            val count =
                                (entries?.tasks?.sumOf { it.count } ?: 0) +
                                    (entries?.google?.size ?: 0)
                            val description =
                                date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
                                    ", " +
                                    stringResource(R.string.calendar_count, count)
                            Column(
                                Modifier.weight(1f)
                                    .heightIn(min = 56.dp)
                                    .testTag("day-${date.toEpochDay()}")
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = description
                                        this.selected = date == selected
                                    }
                                    .clickable { select(date.toEpochDay()) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.size(36.dp)
                                        .then(
                                            if (date == today)
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    CircleShape,
                                                )
                                            else Modifier
                                        )
                                        .then(
                                            if (date == selected)
                                                Modifier.border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape,
                                                )
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color =
                                            when {
                                                date == today ->
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                YearMonth.from(date) != month ->
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else -> MaterialTheme.colorScheme.onSurface
                                            },
                                    )
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    entries
                                        ?.tasks
                                        .orEmpty()
                                        .flatMap { mark ->
                                            List(minOf(mark.count, 3)) { mark.color }
                                        }
                                        .take(3)
                                        .forEach { color ->
                                            Box(
                                                Modifier.size(4.dp)
                                                    .background(
                                                        ListColors[color.mod(12)],
                                                        CircleShape,
                                                    )
                                            )
                                        }
                                    entries?.google.orEmpty().take(1).forEach { event ->
                                        Box(
                                            Modifier.size(4.dp)
                                                .background(
                                                    androidx.compose.ui.graphics.Color(event.colorArgb),
                                                    CircleShape,
                                                )
                                        )
                                    }
                                    if (count > 3)
                                        Text(
                                            "+${count - 3}",
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Text(
                selected.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)) +
                    " · " +
                    stringResource(
                        R.string.calendar_count,
                        content.tasks.size +
                            googleEvents.count { selectedDay in it.startDay until it.endDay },
                    ),
                Modifier.padding(16.dp).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (content.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        else if (content.failed)
            item { Text(stringResource(R.string.error), Modifier.padding(24.dp)) }
        else if (content.tasks.isEmpty() &&
                googleEvents.none { selectedDay in it.startDay until it.endDay }
        )
            item {
                Text(
                    stringResource(R.string.empty_day),
                    Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        items(content.tasks, key = { it.task.id }) { detail ->
            TaskRow(detail, { open(detail) }, { complete(detail.task) })
        }
        items(
            googleEvents.filter { selectedDay in it.startDay until it.endDay },
            key = { "g-" + it.id },
        ) { event ->
            GoogleEventRow(event) { openGoogle(event) }
        }
    }
}

private data class DayDots(val tasks: List<CalendarMark>, val google: List<GoogleEventEntity>)
