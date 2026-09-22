package com.example.pix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.CalendarRules
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.launch

@Composable
fun WeekCalendarScreen(
    selectedDay: Long,
    today: LocalDate,
    marks: List<CalendarMark>,
    content: TaskContent,
    select: (Long) -> Unit,
    open: (TaskWithDetails) -> Unit,
    complete: (TaskEntity) -> Unit,
    add: (Int) -> Unit,
    googleEvents: List<GoogleEventEntity> = emptyList(),
    openGoogle: (GoogleEventEntity) -> Unit = {},
) {
    val agendaState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val date = LocalDate.ofEpochDay(selectedDay)
    val days = CalendarRules.week(date)
    val locale = LocalConfiguration.current.locales[0]
    val format = DateTimeFormatter.ofPattern("d MMM", locale)
    Column {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { select(date.minusWeeks(1).toEpochDay()) },
                modifier = Modifier.testTag("previous-week"),
            ) {
                PixIcon(PixSymbol.BACK, stringResource(R.string.previous_week))
            }
            Text(
                days.first().format(format) +
                    " – " +
                    days.last().format(format) +
                    " · " +
                    days.last().year,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(
                onClick = { select(date.plusWeeks(1).toEpochDay()) },
                modifier = Modifier.testTag("next-week"),
            ) {
                PixIcon(PixSymbol.CHEVRON, stringResource(R.string.next_week))
            }
        }
        Row(
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            days.forEach { day ->
                val marked =
                    marks.any { it.dueDay == day.toEpochDay() } ||
                        googleEvents.any { day.toEpochDay() in it.startDay until it.endDay }
                FilterChip(
                    selected = day == date,
                    onClick = { select(day.toEpochDay()) },
                    modifier = Modifier.testTag("week-day-${day.toEpochDay()}"),
                    label = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale))
                            Text(day.dayOfMonth.toString() + if (marked) " ·" else "")
                        }
                    },
                )
            }
        }
        TextButton(onClick = { select(today.toEpochDay()) }) {
            Text(stringResource(R.string.go_today))
        }
        if (content.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (content.failed) Text(stringResource(R.string.error), Modifier.padding(16.dp))
        val tasks = content.tasks
        val dayGoogle = googleEvents.filter { selectedDay in it.startDay until it.endDay }
        fun spansEarlier(detail: TaskWithDetails) =
            detail.task.minuteOfDay == null || (detail.task.dueDay ?: selectedDay) < selectedDay
        fun googleAllDay(event: GoogleEventEntity) =
            event.allDay || event.startMinute == null || event.startDay < selectedDay
        LazyColumn(
            Modifier.weight(1f).testTag("week-agenda"),
            state = agendaState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.all_day),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(tasks.filter { spansEarlier(it) }, key = { it.task.id }) { detail ->
                TaskRow(detail, { open(detail) }, { complete(detail.task) })
            }
            items(dayGoogle.filter { googleAllDay(it) }, key = { "g-" + it.id }) { event ->
                GoogleEventRow(event) { openGoogle(event) }
            }
            if (tasks.none { spansEarlier(it) } && dayGoogle.none { googleAllDay(it) })
                item {
                    Text(
                        stringResource(R.string.no_all_day),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            (0..23).forEach { hour ->
                item(key = "hour-$hour") {
                    Row(
                        Modifier.fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                val before =
                                    1 +
                                        tasks.count { spansEarlier(it) }.coerceAtLeast(1) +
                                        hour +
                                        tasks.count {
                                            !spansEarlier(it) &&
                                                it.task.minuteOfDay?.let { m -> m / 60 < hour } ==
                                                    true
                                        }
                                scope.launch { agendaState.scrollToItem(before) }
                                add(hour * 60)
                            }
                            .testTag("hour-$hour"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d:00".format(java.util.Locale.ROOT, hour),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 12.dp))
                        PixIcon(
                            PixSymbol.PLUS,
                            stringResource(R.string.add_at_hour, hour),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                items(
                    tasks.filter { !spansEarlier(it) && it.task.minuteOfDay?.div(60) == hour },
                    key = { it.task.id },
                ) { detail ->
                    TaskRow(detail, { open(detail) }, { complete(detail.task) })
                }
                items(
                    dayGoogle.filter {
                        !googleAllDay(it) && (it.startMinute ?: 0) / 60 == hour && it.startDay == selectedDay
                    },
                    key = { "gh-$hour-${it.id}" },
                ) { event ->
                    GoogleEventRow(event) { openGoogle(event) }
                }
            }
        }
    }
}
