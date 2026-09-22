package com.example.pix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.RecurrenceScope
import com.example.pix.domain.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle

@Composable
fun recurrenceLabel(rule: String?): String {
    if (rule == null) return stringResource(R.string.no_repeat)
    val value = RecurrenceRule.parse(rule)
    if (
        value.frequency == Frequency.WEEKLY &&
            value.weekdays == setOf(1, 2, 3, 4, 5) &&
            value.interval == 1
    )
        return stringResource(R.string.weekdays)
    if (value.interval == 1 && value.weekdays.size <= 1)
        return stringResource(
            when (value.frequency) {
                Frequency.DAILY -> R.string.daily
                Frequency.WEEKLY -> R.string.weekly
                Frequency.MONTHLY -> R.string.monthly
                Frequency.YEARLY -> R.string.yearly
            }
        )
    val label =
        pluralStringResource(
            when (value.frequency) {
                Frequency.DAILY -> R.plurals.repeat_days
                Frequency.WEEKLY -> R.plurals.repeat_weeks
                Frequency.MONTHLY -> R.plurals.repeat_months
                Frequency.YEARLY -> R.plurals.repeat_years
            },
            value.interval,
            value.interval,
        )
    val locale = LocalConfiguration.current.locales[0]
    return if (value.frequency == Frequency.WEEKLY && value.weekdays.isNotEmpty())
        label +
            " · " +
            value.weekdays.sorted().joinToString(", ") {
                DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale)
            }
    else label
}

@Composable
fun RecurrenceDialog(initial: String?, day: Long?, dismiss: () -> Unit, choose: (String?) -> Unit) {
    val parsed = remember(initial) { initial?.let(RecurrenceRule::parse) }
    var frequency by remember { mutableStateOf(parsed?.frequency ?: Frequency.DAILY) }
    var interval by remember { mutableStateOf((parsed?.interval ?: 1).toString()) }
    var days by remember {
        mutableStateOf(
            parsed?.weekdays?.ifEmpty {
                setOf(LocalDate.ofEpochDay(day ?: LocalDate.now().toEpochDay()).dayOfWeek.value)
            } ?: setOf(LocalDate.ofEpochDay(day ?: LocalDate.now().toEpochDay()).dayOfWeek.value)
        )
    }
    var custom by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.recurrence)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (!custom) {
                    listOf(
                            R.string.no_repeat to null,
                            R.string.daily to RecurrenceRule(Frequency.DAILY),
                            R.string.weekdays to
                                RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(1, 2, 3, 4, 5)),
                            R.string.weekly to RecurrenceRule(Frequency.WEEKLY, weekdays = days),
                            R.string.monthly to RecurrenceRule(Frequency.MONTHLY),
                            R.string.yearly to RecurrenceRule(Frequency.YEARLY),
                        )
                        .forEach { (label, value) ->
                            TextButton(
                                onClick = {
                                    if (label == R.string.weekly) {
                                        frequency = Frequency.WEEKLY
                                        custom = true
                                    } else choose(value?.encode())
                                },
                                modifier = Modifier.fillMaxWidth().testTag("repeat-$label"),
                            ) {
                                Text(stringResource(label), Modifier.fillMaxWidth())
                            }
                        }
                    TextButton(onClick = { custom = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.custom_repeat), Modifier.fillMaxWidth())
                    }
                } else {
                    OutlinedTextField(
                        interval,
                        { if (it.length <= 2 && it.all(Char::isDigit)) interval = it },
                        label = { Text(stringResource(R.string.interval)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Frequency.entries.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { frequency = f },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(frequency == f, { frequency = f })
                            Text(
                                stringResource(
                                    when (f) {
                                        Frequency.DAILY -> R.string.unit_days
                                        Frequency.WEEKLY -> R.string.unit_weeks
                                        Frequency.MONTHLY -> R.string.unit_months
                                        Frequency.YEARLY -> R.string.unit_years
                                    }
                                )
                            )
                        }
                    }
                    if (frequency == Frequency.WEEKLY) {
                        DayOfWeek.entries.chunked(4).forEach { week ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                week.forEach { d ->
                                    FilterChip(
                                        selected = d.value in days,
                                        onClick = {
                                            days =
                                                if (d.value in days) days - d.value
                                                else days + d.value
                                        },
                                        label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
                                    )
                                }
                            }
                        }
                    }
                    if (frequency == Frequency.MONTHLY || frequency == Frequency.YEARLY)
                        Text(
                            stringResource(R.string.month_end_policy),
                            style = MaterialTheme.typography.bodySmall,
                        )
                }
            }
        },
        confirmButton = {
            if (custom)
                TextButton(
                    enabled =
                        interval.toIntOrNull() in 1..99 &&
                            (frequency != Frequency.WEEKLY || days.isNotEmpty()),
                    onClick = {
                        choose(
                            RecurrenceRule(
                                    frequency,
                                    interval.toInt(),
                                    if (frequency == Frequency.WEEKLY) days else emptySet(),
                                )
                                .encode()
                        )
                    },
                ) {
                    Text(stringResource(R.string.done))
                }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun RecurrenceScopeDialog(
    delete: Boolean = false,
    allowOnly: Boolean = true,
    dismiss: () -> Unit,
    choose: (RecurrenceScope) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(stringResource(if (delete) R.string.delete_repeating else R.string.edit_repeating))
        },
        text = {
            Column {
                Text(
                    stringResource(
                        if (delete) R.string.delete_series_explanation
                        else R.string.scope_explanation
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(enabled = allowOnly, onClick = { choose(RecurrenceScope.ONLY_THIS) }) {
                    Text(stringResource(R.string.only_occurrence))
                }
                TextButton(onClick = { choose(RecurrenceScope.THIS_AND_FUTURE) }) {
                    Text(stringResource(R.string.this_and_future))
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
