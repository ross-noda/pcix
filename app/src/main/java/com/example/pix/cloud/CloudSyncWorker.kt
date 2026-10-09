package com.example.pix.cloud

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.pix.PixApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PixApplication ?: return Result.success()
        // A scheduled worker may wake before auth restore/account ownership is complete.
        // Never let cloud data touch Room until SessionCoordinator has made that account ready.
        when (val session = app.session.state.value) {
            is AccountSessionState.Ready -> app.sync.restoreForAccount(session.user.id)
            AccountSessionState.Restoring, is AccountSessionState.PreparingAccount ->
                return if (runAttemptCount < 5) Result.retry() else Result.failure()
            else -> return Result.success()
        }
        if (!inputData.getBoolean("manual", false) && SyncRetryPolicy.blocked(app.sync.status.value)) return Result.failure()
        return try {
            // A burst of edits may queue several successors. Earlier work can already have
            // uploaded them all; avoid repeating network round trips for those empty requests.
            if (inputData.getBoolean("changes", false) && app.sync.pendingCount() == 0) return Result.success()
            if (app.sync.synchronize()) Result.success()
            else if (SyncRetryPolicy.retry(app.sync.status.value, runAttemptCount)) Result.retry() else Result.failure()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PcixSync", "worker failed before completion")
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

object CloudSyncWork {
    private val manualMutex = kotlinx.coroutines.sync.Mutex()

    /** Enqueue before leaving Settings: WorkManager owns the operation, not the Composable. */
    suspend fun requestNow(context: Context) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        manualMutex.lock()
        try {
            val manager = WorkManager.getInstance(context)
            val current = manager.getWorkInfosForUniqueWork("cloud-sync").get()
            val app = context.applicationContext as PixApplication
            if (current.any { it.state == WorkInfo.State.RUNNING } && !SyncRetryPolicy.blocked(app.sync.status.value)) return@withContext
            manager.enqueueUniqueWork("cloud-sync", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setInputData(workDataOf("manual" to true))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()).result.get()
        } finally { manualMutex.unlock() }
    }

    /** A mutation during push/pull needs a successor; KEEP alone would discard that request. */
    fun enqueueChanges(context: Context) = enqueue(context, ExistingWorkPolicy.APPEND_OR_REPLACE)

    fun enqueue(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

    private fun enqueue(context: Context, policy: ExistingWorkPolicy) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "cloud-sync",
                policy,
                OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setInputData(workDataOf("changes" to (policy == ExistingWorkPolicy.APPEND_OR_REPLACE)))
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build(),
            )
    }

    fun initialize(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "cloud-sync-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build(),
            )
        enqueue(context)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("cloud-sync")
        WorkManager.getInstance(context).cancelUniqueWork("cloud-sync-periodic")
    }
}
