package com.example.pix

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pix.cloud.AccountSessionState
import com.example.pix.cloud.CloudSyncWork
import com.example.pix.google.GoogleCalendarWork
import com.example.pix.reminders.ReminderWork
import com.example.pix.data.TaskEntity
import com.example.pix.ui.AuthScreen
import com.example.pix.ui.PasswordResetScreen
import com.example.pix.ui.SessionErrorScreen
import com.example.pix.ui.LegacyImportScreen
import com.example.pix.ui.PixApp
import com.example.pix.ui.SplashScreen
import com.example.pix.ui.TasksViewModel
import com.example.pix.ui.theme.PixTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthIntent(intent)
        setContent {
            val app = application as PixApplication
            val sessionState by app.session.state.collectAsStateWithLifecycle()
            // Keep drafts across activity recreation, but discard caches when the account changes.
            val modelAccount = (sessionState as? AccountSessionState.Ready)?.user?.id ?: "local-or-signed-out"
            val accountModels: AccountViewModelStore = viewModel()
            val modelOwner = accountModels.forAccount(modelAccount)
            val model: TasksViewModel = viewModel(viewModelStoreOwner = modelOwner,
                key = "tasks:$modelAccount",
                factory = defaultViewModelProviderFactory, extras = defaultViewModelCreationExtras)
            val habitModel: com.example.pix.ui.HabitsViewModel = viewModel(viewModelStoreOwner = modelOwner,
                key = "habits:$modelAccount", factory = defaultViewModelProviderFactory, extras = defaultViewModelCreationExtras)
            LaunchedEffect(model) { handleWidgetIntent(intent, model) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            LaunchedEffect(lifecycle, modelAccount, sessionState is AccountSessionState.Ready) {
                if (app.cloud.configured && sessionState is AccountSessionState.Ready) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (isActive) {
                            CloudSyncWork.enqueue(this@MainActivity)
                            delay(60_000L)
                        }
                    }
                }
            }
            DisposableEffect(lifecycle, model) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_STOP) model.flush()
                    if (event == Lifecycle.Event.ON_RESUME) {
                        ReminderWork.reconcile(this@MainActivity)
                        GoogleCalendarWork.enqueue(this@MainActivity)
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            val theme = model.theme.collectAsStateWithLifecycle()
            val dark = theme.value == 2 || (theme.value == 0 && isSystemInDarkTheme())
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            val accent = model.accent.collectAsStateWithLifecycle()
            SideEffect { LauncherBrand.apply(this@MainActivity, dark) }
            val textSize = model.textSize.collectAsStateWithLifecycle()
            val fontStyle = model.fontStyle.collectAsStateWithLifecycle()
            PixTheme(
                mode = theme.value,
                accent = accent.value,
                textSize = textSize.value,
                fontStyle = fontStyle.value,
            ) {
                androidx.compose.material3.Surface(
                    modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                ) {
                when {
                    !app.cloud.configured -> PixApp(model, habitModel)
                    sessionState is AccountSessionState.Restoring ||
                        sessionState is AccountSessionState.PreparingAccount -> SplashScreen()
                    sessionState is AccountSessionState.SignedOut -> AuthScreen()
                    sessionState is AccountSessionState.PasswordRecovery -> PasswordResetScreen()
                    sessionState is AccountSessionState.LegacyDecision -> LegacyImportScreen()
                    sessionState is AccountSessionState.Error ->
                        SessionErrorScreen((sessionState as AccountSessionState.Error).messageRes)
                    sessionState is AccountSessionState.Ready -> PixApp(model, habitModel)
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
        if (intent.hasExtra("habitId")) recreate()
        if (intent.action == ACTION_NEW_TASK || intent.action == ACTION_OPEN_TASK || intent.action == ACTION_OPEN_PLANNER) recreate()
    }

    private fun handleWidgetIntent(intent: Intent?, model: TasksViewModel) {
        intent?.getStringExtra("habitId")?.let { id ->
            if (runCatching { java.util.UUID.fromString(id) }.isSuccess) model.widgetDestination.value = "habit/$id"
            intent.removeExtra("habitId")
        }
        when (intent?.action) {
            ACTION_NEW_TASK -> {
                val initialDay =
                    intent.takeIf { it.hasExtra(EXTRA_INITIAL_DAY) }
                        ?.getLongExtra(EXTRA_INITIAL_DAY, 0L)
                model.openNew(TaskEntity(title = "", dueDay = initialDay), emptySet())
            }
            ACTION_OPEN_PLANNER -> {
                if (intent.hasExtra(EXTRA_INITIAL_DAY)) model.selectDate(intent.getLongExtra(EXTRA_INITIAL_DAY, 0))
                model.widgetDestination.value = intent.getStringExtra("destination")?.takeIf { it == "calendar" || it == "matrix" || it == "habits" }
            }
            ACTION_OPEN_TASK -> intent.getStringExtra(EXTRA_TASK_ID)?.let(model::openTask)
            else -> intent?.getStringExtra(EXTRA_TASK_ID)?.let(model::openTask)
        }
    }

    companion object {
        const val ACTION_NEW_TASK = "com.example.pix.action.NEW_TASK"
        const val ACTION_OPEN_PLANNER = "com.example.pix.action.OPEN_PLANNER"
        const val ACTION_OPEN_TASK = "com.example.pix.action.OPEN_TASK"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_INITIAL_DAY = "initialDay"
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data?.toString() ?: return
        if (!data.startsWith("com.example.pix://auth")) return
        val app = application as PixApplication
        intent.data = null
        app.backgroundScope.launch { app.auth.handleDeeplink(data) }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchCatching(block: suspend () -> Unit) {
    launch { runCatching { block() } }
}

/** Activity-retained owner; a session transition clears only the former account's models. */
class AccountViewModelStore : ViewModel(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private var account: String? = null

    fun forAccount(id: String): ViewModelStoreOwner {
        if (account != id) {
            viewModelStore.clear()
            account = id
        }
        return this
    }

    override fun onCleared() { viewModelStore.clear() }
}
