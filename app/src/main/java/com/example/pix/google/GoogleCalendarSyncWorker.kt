package com.example.pix.google

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.pix.PixApplication
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class GoogleCalendarSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? PixApplication ?: return Result.success()
        return try {
            app.google.synchronize()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Log.w("PcixGoogle", "calendar sync failed")
            Result.retry()
        }
    }
}

object GoogleCalendarWork {
    fun enqueue(context: Context) {
        if (!(context.applicationContext as PixApplication).google.connected()) return
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "google-calendar-sync",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<GoogleCalendarSyncWorker>()
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
                "google-calendar-periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<GoogleCalendarSyncWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(
                        Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                    )
                    .build(),
            )
        enqueue(context)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("google-calendar-sync")
        WorkManager.getInstance(context).cancelUniqueWork("google-calendar-periodic")
    }
}
