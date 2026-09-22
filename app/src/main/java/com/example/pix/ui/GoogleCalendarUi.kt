package com.example.pix.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.GoogleCalendarEntity
import com.example.pix.data.GoogleEventEntity
import com.example.pix.google.GoogleCalendarRepository
import com.example.pix.google.GoogleCalendarWork
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun GoogleEventRow(event: GoogleEventEntity, open: () -> Unit) {
    val googleLabel = stringResource(R.string.google_event)
    Surface(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().clickable(onClick = open)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp)
                    .background(Color(event.colorArgb), CircleShape)
                    .semantics { contentDescription = googleLabel }
            )
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    eventTimeLabel(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PixIcon(PixSymbol.CALENDAR, stringResource(R.string.google_event))
        }
    }
}

@Composable
fun GoogleEventDetail(event: GoogleEventEntity, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(eventTimeLabel(event))
                if (event.location.isNotBlank()) Text(event.location)
                if (event.description.isNotBlank()) Text(event.description)
                Text(stringResource(R.string.google_readonly))
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        },
    )
}

fun eventTimeLabel(event: GoogleEventEntity): String {
    val start = LocalDate.ofEpochDay(event.startDay)
    val end = LocalDate.ofEpochDay(event.endDay - 1)
    val dates =
        if (event.startDay == event.endDay - 1 || event.endDay <= event.startDay + 1)
            start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        else
            start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) +
                " – " +
                end.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    if (event.allDay || event.startMinute == null) return dates
    fun fmt(m: Int) = "%02d:%02d".format(java.util.Locale.ROOT, m / 60, m % 60)
    return dates + " · " + fmt(event.startMinute) + event.endMinute?.let { "–" + fmt(it) }.orEmpty()
}

@Composable
fun GoogleCalendarSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    val calendars by app.google.calendars.collectAsState(initial = emptyList())
    val reconnect by app.google.needsReconnect.collectAsState()
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            val data = it.data ?: return@rememberLauncherForActivityResult
            val activity = context as? Activity ?: return@rememberLauncherForActivityResult
            scope.launch { app.google.completeAuthorization(activity, data) }
        }
    Surface(shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.google_calendar)) },
                supportingContent = {
                    Text(
                        when {
                            reconnect -> stringResource(R.string.google_reconnect)
                            app.google.connected() ->
                                app.google.email
                                    ?: stringResource(R.string.google_calendars_count, calendars.count { it.enabled })
                            else -> stringResource(R.string.google_not_connected)
                        }
                    )
                },
                modifier =
                    Modifier.clickable {
                        val activity = context as? Activity ?: return@clickable
                        scope.launch {
                            when (val outcome = app.google.authorize(activity)) {
                                GoogleCalendarRepository.AuthOutcome.Ready -> {
                                    GoogleCalendarWork.enqueue(app)
                                    picker = true
                                }
                                is GoogleCalendarRepository.AuthOutcome.Resolution ->
                                    launcher.launch(IntentSenderRequest.Builder(outcome.sender).build())
                                GoogleCalendarRepository.AuthOutcome.Failed -> Unit
                            }
                        }
                    },
            )
            if (app.google.connected()) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_choose)) },
                    modifier = Modifier.clickable { picker = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_refresh)) },
                    modifier =
                        Modifier.clickable {
                            scope.launch {
                                runCatching { app.google.synchronize(context as? Activity) }
                                GoogleCalendarWork.enqueue(app)
                            }
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_disconnect)) },
                    modifier =
                        Modifier.clickable {
                            scope.launch { app.google.disconnect(context as? Activity) }
                        },
                )
            }
        }
    }
    if (picker)
        AlertDialog(
            onDismissRequest = { picker = false },
            title = { Text(stringResource(R.string.google_choose)) },
            text = {
                Column {
                    calendars.forEach { calendar -> CalendarToggle(calendar, app) }
                }
            },
            confirmButton = {
                TextButton(onClick = { picker = false }) { Text(stringResource(R.string.close)) }
            },
        )
}

@Composable
private fun CalendarToggle(calendar: GoogleCalendarEntity, app: PixApplication) {
    val scope = rememberCoroutineScope()
    Row(
        Modifier.fillMaxWidth().clickable {
            scope.launch {
                app.google.setEnabled(calendar.id, !calendar.enabled)
                GoogleCalendarWork.enqueue(app)
            }
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(calendar.enabled, { enabled ->
            scope.launch {
                app.google.setEnabled(calendar.id, enabled)
                GoogleCalendarWork.enqueue(app)
            }
        })
        Box(Modifier.size(10.dp).background(Color(calendar.colorArgb), CircleShape))
        Text(calendar.summary, Modifier.padding(start = 12.dp))
    }
}
