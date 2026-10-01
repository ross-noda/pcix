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
import androidx.compose.ui.semantics.liveRegion
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
fun AuthScreen(model: AuthFormViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as PixApplication
    val state by app.auth.state.collectAsState()
    val awaiting = state as? AuthState.AwaitingEmail
    val keyboard = LocalSoftwareKeyboardController.current
    fun submit() { keyboard?.hide(); model.submit() }
    Surface(color=MaterialTheme.colorScheme.background,contentColor=MaterialTheme.colorScheme.onBackground) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
            BrandWordmark(Modifier.padding(bottom=8.dp).semantics {heading()})
            Text(stringResource(R.string.auth_tagline),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if(!app.cloud.configured) Text(stringResource(R.string.cloud_missing_config),color=MaterialTheme.colorScheme.error)
            if(awaiting!=null) {
                Text(stringResource(R.string.auth_check_email),style=MaterialTheme.typography.headlineSmall,modifier=Modifier.semantics {heading()})
                Text(stringResource(R.string.auth_email_sent_to,awaiting.email),Modifier.padding(vertical=16.dp))
                Text(stringResource(R.string.auth_verify_email),color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick={model.resend(awaiting.email)},enabled=!model.busy,modifier=Modifier.fillMaxWidth().padding(top=16.dp)) {Text(stringResource(R.string.auth_resend))}
                TextButton(onClick={model.email=awaiting.email;model.loginMode()},enabled=!model.busy) {Text(stringResource(R.string.auth_back_login))}
            } else {
                OutlinedTextField(model.email,{model.email=it},label={Text(stringResource(R.string.email))},enabled=!model.busy,singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email,imeAction=ImeAction.Next),modifier=Modifier.fillMaxWidth().semantics {contentDescription="email"})
                if(!model.recover) AuthPasswordField(model.password,{model.password=it},R.string.password,!model.busy,if(model.register) ImeAction.Next else ImeAction.Done,::submit)
                if(model.register && !model.recover) AuthPasswordField(model.confirm,{model.confirm=it},R.string.confirm_password,!model.busy,ImeAction.Done,::submit)
                Button(onClick=::submit,enabled=app.cloud.configured && !model.busy,modifier=Modifier.fillMaxWidth().padding(top=16.dp).heightIn(min=48.dp)) {
                    Text(stringResource(when {model.recover->R.string.auth_send_reset;model.register->R.string.auth_register;else->R.string.auth_login}))
                }
                val error=(state as? AuthState.Error)?.messageRes
                if(error==R.string.auth_email_unconfirmed || error==R.string.auth_invalid_callback) TextButton(onClick={model.resend()},enabled=!model.busy) {Text(stringResource(R.string.auth_resend))}
                TextButton(onClick=model::toggleRegistration,enabled=!model.busy) {Text(stringResource(if(model.register) R.string.auth_have_account else R.string.auth_need_account))}
                TextButton(onClick=model::toggleRecovery,enabled=!model.busy) {Text(stringResource(if(model.recover) R.string.auth_back_login else R.string.auth_forgot))}
                OutlinedButton(onClick={(context as? Activity)?.let(model::google)},enabled=app.cloud.configured && !model.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(stringResource(R.string.auth_google))}
            }
            val message=model.message.takeIf {it!=0} ?: (state as? AuthState.Error)?.messageRes
            if(message!=null) Text(stringResource(message),color=if(model.success) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                modifier=Modifier.fillMaxWidth().padding(top=12.dp).semantics {liveRegion=androidx.compose.ui.semantics.LiveRegionMode.Polite})
            if(model.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=16.dp))
        }
    }
}

@Composable
private fun AuthPasswordField(value:String,change:(String)->Unit,label:Int,enabled:Boolean,action:ImeAction,submit:()->Unit) {
    var visible by remember {mutableStateOf(false)}
    OutlinedTextField(value,change,label={Text(stringResource(label))},singleLine=true,enabled=enabled,
        visualTransformation=if(visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon={TextButton(onClick={visible=!visible}) {Text(stringResource(if(visible) R.string.auth_hide_password else R.string.auth_show_password))}},
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password,imeAction=action),keyboardActions=KeyboardActions(onDone={submit()}),modifier=Modifier.fillMaxWidth().padding(top=8.dp))
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
