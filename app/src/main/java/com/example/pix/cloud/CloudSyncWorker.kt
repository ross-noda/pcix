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
        if (app.session.state.value !is AccountSessionState.Ready) return Result.success()
        return try {
            if (app.sync.synchronize()) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PcixSync", "worker failed")
            Result.retry()
        }
    }
}

object CloudSyncWork {
    fun enqueue(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "cloud-sync",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CloudSyncWorker>()
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
