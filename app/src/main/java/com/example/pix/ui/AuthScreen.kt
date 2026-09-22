package com.example.pix.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.cloud.AuthException
import com.example.pix.cloud.AuthState
import com.example.pix.cloud.GoogleSignInHelper
import kotlinx.coroutines.launch

@Composable
fun AuthScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    val state by app.auth.state.collectAsState()
    val scope = rememberCoroutineScope()
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var recover by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableIntStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    val configured = app.cloud.configured
    fun submit() {
        if (busy) return
        if (email.isBlank() || !email.contains('@')) {
            message = R.string.auth_invalid_email
            return
        }
        if (register && password != confirm) {
            message = R.string.auth_password_mismatch
            return
        }
        if (password.length < 6) {
            message = R.string.auth_weak_password
            return
        }
        busy = true
        message = 0
        keyboard?.hide()
        scope.launch {
            val result =
                if (register) app.auth.signUp(email, password) else app.auth.signIn(email, password)
            busy = false
            result.exceptionOrNull()?.let {
                message = (app.auth.state.value as? AuthState.Error)?.messageRes ?: R.string.auth_generic
            }
            if (register && result.isSuccess && app.auth.state.value is AuthState.Unauthenticated) {
                message = R.string.auth_verify_email
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandWordmark(Modifier.padding(bottom = 8.dp).semantics { heading() })
        Text(
            stringResource(R.string.auth_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (!configured) {
            Text(
                stringResource(R.string.cloud_missing_config),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
        }
        OutlinedTextField(
            email,
            { email = it },
            label = { Text(stringResource(R.string.email)) },
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "email" },
        )
        if (!recover) {
            OutlinedTextField(
                password,
                { password = it },
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (register) ImeAction.Next else ImeAction.Done,
                    ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        if (register && !recover) {
            OutlinedTextField(
                confirm,
                { confirm = it },
                label = { Text(stringResource(R.string.confirm_password)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        val error = (state as? AuthState.Error)?.messageRes
        if (message != 0)
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            )
        else if (error != null && error != message)
            Text(
                stringResource(error),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            )
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        Button(
            onClick = {
                if (recover) {
                    if (email.isBlank() || !email.contains('@')) {
                        message = R.string.auth_invalid_email
                    } else {
                        busy = true
                        message = 0
                        scope.launch {
                            val result = app.auth.recover(email)
                            busy = false
                            message =
                                if (result.isSuccess) R.string.auth_recover_sent
                                else (result.exceptionOrNull() as? AuthException)?.messageRes
                                    ?: (app.auth.state.value as? AuthState.Error)?.messageRes
                                    ?: R.string.auth_generic
                        }
                    }
                } else submit()
            },
            enabled = configured && !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp),
        ) {
            Text(
                stringResource(
                    when {
                        recover -> R.string.auth_send_reset
                        register -> R.string.auth_register
                        else -> R.string.auth_login
                    }
                )
            )
        }
        TextButton(
            onClick = {
                recover = false
                register = !register
                message = 0
            }
        ) {
            Text(stringResource(if (register) R.string.auth_have_account else R.string.auth_need_account))
        }
        TextButton(onClick = { recover = !recover }) {
            Text(stringResource(if (recover) R.string.auth_back_login else R.string.auth_forgot))
        }
        OutlinedButton(
            onClick = {
                val activity = context as? Activity ?: return@OutlinedButton
                busy = true
                message = 0
                scope.launch {
                    runCatching {
                            val helper = GoogleSignInHelper(app.cloud)
                            val token = helper.token(activity)
                            app.auth.signInGoogle(token.idToken, token.nonce)
                        }
                        .onFailure {
                            message = R.string.auth_google_failed
                        }
                    busy = false
                }
            },
            enabled = configured && app.cloud.googleConfigured && !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.auth_google))
        }
    }
}

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandWordmark()
            LinearProgressIndicator(Modifier.padding(top = 24.dp).width(160.dp))
        }
    }
}

@Composable
fun LegacyImportScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    var counts by remember { mutableStateOf(com.example.pix.cloud.LegacyCounts(0, 0, 0)) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { counts = app.accounts.counts() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.legacy_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.legacy_body, counts.tasks, counts.lists, counts.tags))
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                busy = true
                scope.launch {
                    app.accounts.importLegacy()
                    app.session.onImported()
                    com.example.pix.cloud.CloudSyncWork.enqueue(app)
                    busy = false
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.legacy_import))
        }
        TextButton(
            onClick = {
                busy = true
                scope.launch {
                    app.accounts.replaceWithCloud()
                    app.session.onImported()
                    com.example.pix.cloud.CloudSyncWork.enqueue(app)
                    busy = false
                }
            },
            enabled = !busy,
        ) {
            Text(stringResource(R.string.legacy_discard))
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}


@Composable
fun PasswordResetScreen() {
    val app = LocalContext.current.applicationContext as PixApplication
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableIntStateOf(0) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandWordmark(Modifier.padding(bottom = 16.dp).semantics { heading() })
        Text(
            stringResource(R.string.auth_choose_new_password),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            password,
            { password = it },
            label = { Text(stringResource(R.string.auth_new_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            confirm,
            { confirm = it },
            label = { Text(stringResource(R.string.confirm_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (message != 0) {
            Text(
                stringResource(message),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 16.dp))
        Button(
            onClick = {
                when {
                    password.length < 6 -> message = R.string.auth_weak_password
                    password != confirm -> message = R.string.auth_password_mismatch
                    else -> {
                        busy = true
                        message = 0
                        scope.launch {
                            val result = app.auth.updatePassword(password)
                            busy = false
                            if (result.isFailure) {
                                message =
                                    (result.exceptionOrNull() as? AuthException)?.messageRes
                                        ?: R.string.auth_generic
                            }
                        }
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.auth_update_password))
        }
    }
}

@Composable
fun SessionErrorScreen(messageRes: Int) {
    val app = LocalContext.current.applicationContext as PixApplication
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandWordmark(Modifier.padding(bottom = 16.dp))
        Text(stringResource(messageRes), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { scope.launch { app.auth.signOut() } }) {
            Text(stringResource(R.string.logout))
        }
    }
}
