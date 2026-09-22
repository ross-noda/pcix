package com.example.pix.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.TaskEntity
import com.example.pix.domain.TaskTiming
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun durationEndLabel(task: TaskEntity): String {
    val end = TaskTiming.end(task) ?: return ""
    val date = if (task.minuteOfDay == null) end.toLocalDate().minusDays(1) else end.toLocalDate()
    val value =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) +
            if (task.minuteOfDay == null) ""
            else " · " + end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    return stringResource(R.string.ends_at, value)
}

@Composable
fun DurationFields(task: TaskEntity, edit: (TaskEntity) -> Unit) {
    if (task.dueDay == null) return
    val context = LocalContext.current
    var invalid by remember(task.id) { mutableStateOf(false) }
    val start = TaskTiming.start(task)!!
    val end = TaskTiming.end(task)
    fun changeEnd(value: LocalDateTime) {
        val minutes = Duration.between(start, value).toMinutes()
        invalid = minutes !in 1..TaskTiming.MAX_MINUTES.toLong()
        if (!invalid) edit(task.copy(durationMinutes = minutes.toInt()))
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.task_duration), Modifier.weight(1f))
        Switch(
            checked = end != null,
            onCheckedChange = {
                invalid = false
                edit(
                    task.copy(
                        durationMinutes =
                            if (it) {
                                if (task.minuteOfDay == null) 1440 else 60
                            } else null
                    )
                )
            },
            modifier = Modifier.testTag("duration-enabled"),
        )
    }
    if (end != null) {
        val displayedDay =
            if (task.minuteOfDay == null) end.toLocalDate().minusDays(1) else end.toLocalDate()
        Text(
            durationEndLabel(task),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("duration-end"),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val date = LocalDate.of(year, month + 1, day)
                                changeEnd(
                                    if (task.minuteOfDay == null) date.plusDays(1).atStartOfDay()
                                    else date.atTime(end.toLocalTime())
                                )
                            },
                            displayedDay.year,
                            displayedDay.monthValue - 1,
                            displayedDay.dayOfMonth,
                        )
                        .show()
                }
            ) {
                Text(stringResource(R.string.end_date))
            }
            if (task.minuteOfDay != null)
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    changeEnd(end.toLocalDate().atTime(hour, minute))
                                },
                                end.hour,
                                end.minute,
                                true,
                            )
                            .show()
                    }
                ) {
                    Text(stringResource(R.string.end_time))
                }
        }
        if (task.minuteOfDay != null)
            ChoiceRow(
                listOf(30, 60, 90, 120).map {
                    it.toString() to stringResource(R.string.duration_minutes, it)
                },
                task.durationMinutes.toString(),
            ) {
                invalid = false
                edit(task.copy(durationMinutes = it.toInt()))
            }
        else
            Text(
                stringResource(R.string.duration_days, task.durationMinutes!! / 1440),
                style = MaterialTheme.typography.bodySmall,
            )
    }
    if (invalid)
        Text(stringResource(R.string.invalid_duration), color = MaterialTheme.colorScheme.error)
}
