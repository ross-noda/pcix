package com.example.pix.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.reminders.ReminderWork

@Composable
fun ReminderControls(showSwitch: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    val scheduler = app.scheduler
    var sound by remember { mutableStateOf(scheduler.soundEnabled()) }
    var allowed by remember { mutableStateOf(scheduler.notificationsAllowed()) }
    var exact by remember { mutableStateOf(scheduler.exactAllowed()) }
    var enabled by remember { mutableStateOf(scheduler.enabled()) }
    var explanation by remember { mutableStateOf(false) }
    var exactExplanation by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("reminder_permission", 0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            allowed = scheduler.notificationsAllowed()
            ReminderWork.reconcile(context)
        }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sound = scheduler.soundEnabled()
                allowed = scheduler.notificationsAllowed()
                exact = scheduler.exactAllowed()
                enabled = scheduler.enabled()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    // Prompt only in context of a timed task, never at application startup.
    LaunchedEffect(Unit) {
        if (!showSwitch && !allowed && !prefs.getBoolean("explained", false)) explanation = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showSwitch)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.reminders),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        scheduler.setEnabled(it)
                    },
                )
            }
        Text(
            stringResource(
                when {
                    !enabled -> R.string.reminders_disabled
                    !allowed -> R.string.notifications_denied
                    !exact -> R.string.reminder_inexact
                    else -> R.string.reminder_ready
                }
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        if (enabled && !allowed)
            TextButton(onClick = { explanation = true }) {
                Text(stringResource(R.string.enable_notifications))
            }
        if (enabled && allowed && !exact)
            TextButton(onClick = { exactExplanation = true }) {
                Text(stringResource(R.string.enable_exact))
            }
        if (showSwitch)
            Text(
                stringResource(
                    if (sound) R.string.notification_sound_on else R.string.notification_sound_off
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        if (showSwitch)
            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(
                                Settings.EXTRA_CHANNEL_ID,
                                com.example.pix.reminders.REMINDER_CHANNEL,
                            )
                    )
                }
            ) {
                Text(stringResource(R.string.notification_settings))
            }
    }
    if (explanation)
        AlertDialog(
            onDismissRequest = {
                explanation = false
                prefs.edit { putBoolean("explained", true) }
            },
            title = { Text(stringResource(R.string.reminders)) },
            text = { Text(stringResource(R.string.notification_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        explanation = false
                        val requested = prefs.getBoolean("requested", false)
                        prefs
                            .edit()
                            .putBoolean("explained", true)
                            .putBoolean("requested", true)
                            .apply()
                        if (Build.VERSION.SDK_INT >= 33 && !requested)
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                    }
                ) {
                    Text(stringResource(R.string.enable_notifications))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        explanation = false
                        prefs.edit { putBoolean("explained", true) }
                    }
                ) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
    if (exactExplanation)
        AlertDialog(
            onDismissRequest = { exactExplanation = false },
            title = { Text(stringResource(R.string.enable_exact)) },
            text = { Text(stringResource(R.string.exact_explanation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        exactExplanation = false
                        if (Build.VERSION.SDK_INT >= 31)
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    "package:${context.packageName}".toUri(),
                                )
                            )
                    }
                ) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { exactExplanation = false }) {
                    Text(stringResource(R.string.not_now))
                }
            },
        )
}
