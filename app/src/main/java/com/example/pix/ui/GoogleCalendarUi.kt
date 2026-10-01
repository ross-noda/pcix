package com.example.pix.ui

import android.app.Activity
import android.accounts.AccountManager
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.CancellationException
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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
    val app = LocalContext.current.applicationContext as PixApplication
    val calendars by app.google.calendars.collectAsState(initial = emptyList())
    val calendar = calendars.firstOrNull { it.accountId == event.accountId && it.id == event.calendarId }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(event.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(calendar?.summary ?: event.calendarId)
                Text(eventTimeLabel(event))
                if (event.allDay) Text(stringResource(R.string.google_all_day))
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
    val end = LocalDate.ofEpochDay(if (!event.allDay && event.endMinute == 0) event.endDay else event.endDay - 1)
    val dates =
        if (start == end)
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
    val connection by app.google.connection.collectAsState()
    var picker by rememberSaveable { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val busy = connection.busy || refreshing
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            app.google.connectionCancelled(result.resultCode == Activity.RESULT_OK)
        } else app.backgroundScope.launch { app.google.completeAuthorization(data = result.data!!) }
    }
    val accountPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (result.resultCode != Activity.RESULT_OK || email.isNullOrBlank()) {
            app.google.connectionCancelled(result.resultCode == Activity.RESULT_OK)
        } else app.backgroundScope.launch { app.google.authorize(accountEmail = email) }
    }
    LaunchedEffect(connection.resolution) {
        connection.resolution?.let { sender ->
            try {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
                app.google.resolutionLaunched()
            } catch (error: Exception) { app.google.reportFailure(error, "launch-consent") }
        }
    }
    val errorLabel = when (connection.issue) {
        GoogleCalendarRepository.Issue.Cancelled -> R.string.google_cancelled
        GoogleCalendarRepository.Issue.Callback -> R.string.google_callback_error
        GoogleCalendarRepository.Issue.Configuration -> R.string.google_configuration_error
        GoogleCalendarRepository.Issue.Permission -> R.string.google_permission_error
        GoogleCalendarRepository.Issue.Network -> R.string.google_network_error
        GoogleCalendarRepository.Issue.Other -> R.string.google_sync_error
        null -> null
    }
    Surface(shape = MaterialTheme.shapes.large) {
        Column {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (errorLabel != null) Text(stringResource(errorLabel), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            ListItem(
                headlineContent = { Text(stringResource(R.string.google_calendar)) },
                supportingContent = {
                    Text(
                        when {
                            connection.busy -> stringResource(R.string.google_connecting)
                            reconnect -> stringResource(R.string.google_reconnect)
                            connection.email != null ->
                                connection.email
                                    ?: stringResource(R.string.google_calendars_count, calendars.count { it.enabled })
                            else -> stringResource(R.string.google_not_connected)
                        }
                    )
                },
                modifier =
                    Modifier.clickable(enabled = !busy) {
                        app.google.beginSelection()
                        try {
                            accountPicker.launch(AccountManager.newChooseAccountIntent(
                                null, null, arrayOf("com.google"), null, null, null, null))
                        } catch (error: Exception) { app.google.reportFailure(error, "launch-account-picker") }
                    },
            )
            if (connection.email != null) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_choose)) },
                    modifier = Modifier.clickable { picker = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_refresh)) },
                    modifier =
                        Modifier.clickable {
                            if (!busy) {
                                refreshing = true
                                app.backgroundScope.launch {
                                    try { app.google.synchronize() }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (error: Exception) { app.google.reportFailure(error, "manual-sync") }
                                    finally { refreshing = false }
                                }
                            }
                        },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.google_disconnect)) },
                    modifier =
                        Modifier.clickable {
                            if (!busy) app.backgroundScope.launch {
                                try { app.google.disconnect(null) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { app.google.reportFailure(error, "disconnect") }
                            }
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
                Column(Modifier.verticalScroll(rememberScrollState())) {
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
