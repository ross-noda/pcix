package com.example.pix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.cloud.AuthException
import com.example.pix.cloud.AuthState
import com.example.pix.cloud.CloudSyncStatus
import com.example.pix.cloud.CloudSyncWork
import com.example.pix.google.GoogleCalendarWork
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch

@Composable
fun AccountStatusLine() {
    val app = LocalContext.current.applicationContext as PixApplication
    val auth by app.auth.state.collectAsState()
    val text =
        when {
            !app.cloud.configured -> stringResource(R.string.local_mode)
            auth is AuthState.Authenticated -> (auth as AuthState.Authenticated).user.email
            else -> stringResource(R.string.local_mode)
        }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun AccountSettings(model: TasksViewModel) {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    val auth by app.auth.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var changePassword by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    if (auth !is AuthState.Authenticated) return
    Surface(shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.account)) },
                supportingContent = { Text((auth as AuthState.Authenticated).user.email) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.auth_change_password)) },
                modifier = Modifier.clickable { changePassword = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.logout)) },
                modifier = Modifier.clickable { confirmLogout = true },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.delete_account)) },
                modifier = Modifier.clickable { confirmDelete = true },
            )
        }
    }
    if (changePassword) {
        var password by remember { mutableStateOf("") }
        var confirm by remember { mutableStateOf("") }
        var currentPassword by remember { mutableStateOf("") }
        var nonce by remember { mutableStateOf("") }
        var requireCurrent by remember { mutableStateOf(false) }
        var requireNonce by remember { mutableStateOf(false) }
        var error by remember { mutableIntStateOf(0) }
        var info by remember { mutableIntStateOf(0) }
        AlertDialog(
            onDismissRequest = { if (!pending) changePassword = false },
            title = { Text(stringResource(R.string.auth_change_password)) },
            text = {
                Column {
                    if (requireCurrent) {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it },
                            label = { Text(stringResource(R.string.auth_current_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.auth_new_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(top = if (requireCurrent) 8.dp else 0.dp),
                    )
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        label = { Text(stringResource(R.string.confirm_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (requireNonce) {
                        OutlinedTextField(
                            value = nonce,
                            onValueChange = { nonce = it },
                            label = { Text(stringResource(R.string.auth_reauthentication_code)) },
                            singleLine = true,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (info != 0) {
                        Text(
                            stringResource(info),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    if (error != 0) {
                        Text(
                            stringResource(error),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !pending,
                    onClick = {
                        when {
                            password.length < 6 -> error = R.string.auth_weak_password
                            password != confirm -> error = R.string.auth_password_mismatch
                            requireCurrent && currentPassword.isBlank() ->
                                error = R.string.auth_current_password_required
                            requireNonce && nonce.isBlank() ->
                                error = R.string.auth_reauthentication_required
                            else -> scope.launch {
                                pending = true
                                error = 0
                                val result =
                                    app.auth.updatePassword(
                                        newPassword = password,
                                        currentPassword = currentPassword.takeIf { requireCurrent },
                                        nonce = nonce.takeIf { requireNonce },
                                    )
                                if (result.isSuccess) {
                                    pending = false
                                    changePassword = false
                                    return@launch
                                }
                                val messageRes =
                                    (result.exceptionOrNull() as? AuthException)?.messageRes
                                        ?: R.string.auth_generic
                                when (messageRes) {
                                    R.string.auth_reauthentication_required -> {
                                        val reauth = app.auth.requestPasswordReauthentication()
                                        if (reauth.isSuccess) {
                                            requireNonce = true
                                            info = R.string.auth_reauthentication_code_sent
                                            error = 0
                                        } else {
                                            error =
                                                (reauth.exceptionOrNull() as? AuthException)?.messageRes
                                                    ?: R.string.auth_generic
                                        }
                                    }
                                    R.string.auth_current_password_required,
                                    R.string.auth_current_password_invalid -> {
                                        requireCurrent = true
                                        error = messageRes
                                    }
                                    else -> error = messageRes
                                }
                                pending = false
                            }
                        }
                    },
                ) { Text(stringResource(R.string.auth_update_password)) }
            },
            dismissButton = {
                TextButton(enabled = !pending, onClick = { changePassword = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (confirmLogout)
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout)) },
            text = { Text(stringResource(R.string.logout_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLogout = false
                        model.flush()
                        scope.launch {
                            pending = true
                            runCatching { app.sync.synchronize() }
                            CloudSyncWork.cancel(app)
                            GoogleCalendarWork.cancel(app)
                            runCatching { app.google.disconnect(null) }
                            app.auth.signOut()
                            app.accounts.wipeUserData()
                            app.accounts.clearGoogle()
                            app.accounts.setOwner(null)
                            pending = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_account)) },
            text = { Text(stringResource(R.string.delete_account_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        scope.launch {
                            runCatching { app.auth.deleteAccount() }
                            CloudSyncWork.cancel(app)
                            app.accounts.wipeUserData()
                            app.accounts.clearGoogle()
                            app.accounts.setOwner(null)
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete_account))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    if (pending) LinearProgressIndicator(Modifier.fillMaxWidth().padding(8.dp))
}

@Composable
fun DataSyncSettings() {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    val status by app.sync.status.collectAsState()
    val last by app.sync.lastSuccess.collectAsState()
    val scope = rememberCoroutineScope()
    val label =
        when (status) {
            CloudSyncStatus.Syncing -> stringResource(R.string.sync_syncing)
            CloudSyncStatus.Offline -> stringResource(R.string.sync_offline)
            CloudSyncStatus.Error -> stringResource(R.string.sync_error)
            CloudSyncStatus.Unconfigured -> stringResource(R.string.cloud_missing_config)
            CloudSyncStatus.Idle ->
                if (last == 0L) stringResource(R.string.sync_idle)
                else
                    stringResource(
                        R.string.sync_last,
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                            .withZone(ZoneId.systemDefault())
                            .format(Instant.ofEpochMilli(last)),
                    )
        }
    Surface(shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.sync_now)) },
                supportingContent = { Text(label) },
                modifier =
                    Modifier.clickable(enabled = app.cloud.configured) {
                        scope.launch { app.sync.synchronize() }
                    },
            )
        }
    }
}
