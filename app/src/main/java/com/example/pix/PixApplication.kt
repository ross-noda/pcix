package com.example.pix

import android.app.Application
import com.example.pix.cloud.*
import com.example.pix.data.PixDatabase
import com.example.pix.data.TaskRepository
import com.example.pix.google.GoogleCalendarRepository
import com.example.pix.google.GoogleCalendarWork
import com.example.pix.reminders.*
import com.example.pix.widget.TaskWidgetUpdater
import kotlinx.coroutines.*

class PixApplication : Application() {
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { PixDatabase.create(this) }
    val scheduler by lazy { ReminderScheduler(this) }
    val reminders by lazy { ReminderEngine(database, scheduler) }
    val cloud by lazy { CloudConfig.fromBuild() }
    val http by lazy { CloudHttp(cloud) }
    val auth by lazy { AuthRepository(this, cloud, http) }
    val remote by lazy { RemoteDataSource(cloud, http) }
    val accounts by lazy { AccountStore(this, database) }
    val session by lazy { SessionCoordinator(auth, accounts, backgroundScope) }
    val sync by lazy {
        SyncEngine(database, auth, remote) { ReminderWork.reconcile(this) }
    }
    val accountLifecycle by lazy {
        AccountLifecycleManager(
            accounts = accounts,
            syncNow = { sync.synchronize() },
            signOutLocal = { auth.signOut() },
            deleteRemote = { auth.deleteAccount() },
            beforeLocalClear = {
                // Pcix account lifecycle is intentionally independent from Google Calendar.
                CloudSyncWork.cancel(this)
            },
        )
    }
    val google by lazy { GoogleCalendarRepository(this, database, http, cloud) }
    val widgetUpdater by lazy { TaskWidgetUpdater(this, database, backgroundScope) }
    val repository by lazy {
        TaskRepository(
            database,
            { ReminderWork.reconcile(this) },
            { if (cloud.configured) CloudSyncWork.enqueue(this) },
        )
    }

    override fun onCreate() {
        super.onCreate()
        ReminderWork.initialize(this)
        widgetUpdater.start()
        if (cloud.configured) {
            CloudSyncWork.initialize(this)
            session.start()
        }
        GoogleCalendarWork.initialize(this)
        backgroundScope.launch {
            runCatching {
                com.example.pix.data
                    .ImageStore(this@PixApplication)
                    .prune(database.dao().referencedImages().toSet())
            }
        }
        if (cloud.configured) {
            backgroundScope.launch {
                session.state.collect { state ->
                    if (state is AccountSessionState.Ready) {
                        sync.restoreForAccount(state.user.id)
                        CloudSyncWork.enqueue(this@PixApplication)
                    }
                }
            }
        }
    }
}
