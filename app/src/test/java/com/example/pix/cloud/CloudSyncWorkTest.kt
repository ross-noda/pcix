package com.example.pix.cloud

import android.app.Application
import android.content.Context
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CloudSyncWorkTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var manager: WorkManager
    private val activeWorkers = mutableListOf<GateWorker>()

    @Before fun initialize() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor())
                .setWorkerFactory(object : WorkerFactory() {
                    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                        GateWorker(appContext, workerParameters).also { activeWorkers.add(it) }
                }).build(),
        )
        manager = WorkManager.getInstance(context)
    }

    @After fun close() {
        manager.cancelAllWork().result.get()
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun work() = manager.getWorkInfosForUniqueWork("cloud-sync").get()

    @Test fun foregroundRefreshCoalescesWhileOffline() {
        repeat(4) { CloudSyncWork.enqueue(context) }
        val pending = work()
        assertEquals(1, pending.size)
        assertEquals(WorkInfo.State.ENQUEUED, pending.single().state)
        assertEquals(androidx.work.NetworkType.CONNECTED, pending.single().constraints.requiredNetworkType)
    }

    @Test fun localChangeAlwaysRetainsASuccessorToAnExistingSync() {
        CloudSyncWork.enqueue(context)
        val first = work().single()
        val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
        driver.setAllConstraintsMet(first.id)
        assertEquals(WorkInfo.State.RUNNING, manager.getWorkInfoById(first.id).get()!!.state)
        CloudSyncWork.enqueueChanges(context)
        val pending = work()
        assertEquals(2, pending.size)
        assertEquals(WorkInfo.State.BLOCKED, pending.single { it.id != first.id }.state)
        activeWorkers.first().succeed()
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(first.id).get()!!.state)
        val successor = work().single { it.id != first.id }
        driver.setAllConstraintsMet(successor.id)
        activeWorkers.last().succeed()
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(successor.id).get()!!.state)
    }

    // Control the lifetime of real WorkManager jobs without accessing accounts or the network.
    private class GateWorker(context: Context, params: WorkerParameters) : ListenableWorker(context, params) {
        private lateinit var completion: CallbackToFutureAdapter.Completer<Result>
        override fun startWork() = CallbackToFutureAdapter.getFuture<Result> {
            completion = it
            "cloud-sync-test"
        }
        fun succeed() { completion.set(Result.success()) }
    }

    @Test fun changesRestartACancelledChain() {
        CloudSyncWork.enqueueChanges(context)
        manager.cancelUniqueWork("cloud-sync").result.get()
        CloudSyncWork.enqueueChanges(context)
        assertEquals(1, work().count { it.state == WorkInfo.State.ENQUEUED })
    }

    @Test fun initializationIsIdempotentAndLogoutCancelsBothSchedules() {
        repeat(3) { CloudSyncWork.initialize(context) }
        assertEquals(1, work().size)
        val periodic = manager.getWorkInfosForUniqueWork("cloud-sync-periodic").get()
        assertEquals(1, periodic.size)
        assertEquals(15 * 60 * 1000L, periodic.single().periodicityInfo!!.repeatIntervalMillis)
        CloudSyncWork.cancel(context)
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(manager.getWorkInfosForUniqueWork("cloud-sync-periodic").get().all { it.state == WorkInfo.State.CANCELLED })
    }
}
