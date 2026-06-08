package net.kikin.nubecita.feature.chats.impl.worker

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * `androidx.work:work-testing` coverage for the background DM-poll worker (§9.2).
 *
 * The pure orchestration ([DmPollRunner]) and the schedule/cancel decision
 * ([DmPollScheduler]) are exhaustively JVM-unit-tested; this verifies the two
 * pieces that need the Android runtime:
 * 1. [WorkManagerDmWorkScheduler] enqueues a unique **periodic** request with
 *    the `NetworkType.CONNECTED` constraint at the 15-minute floor (design D1).
 * 2. [DmPollWorker] is constructable through Hilt's [HiltWorkerFactory] and its
 *    `doWork()` runs end-to-end (signed-out → no-op success, no network).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DmPollWorkInstrumentationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        hiltRule.inject()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
    }

    @Test
    fun ensureScheduled_enqueuesPeriodicWorkWithNetworkConstraint() {
        runBlocking {
            WorkManagerDmWorkScheduler(context, Dispatchers.Unconfined).ensureScheduled()
        }

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        assertEquals(1, infos.size)
        val info = infos.first()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(
            TimeUnit.MINUTES.toMillis(15),
            info.periodicityInfo?.repeatIntervalMillis,
        )
    }

    @Test
    fun cancel_removesTheUniqueWork() {
        runBlocking {
            val scheduler = WorkManagerDmWorkScheduler(context, Dispatchers.Unconfined)
            scheduler.ensureScheduled()
            scheduler.cancel()
        }

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        // Cancel leaves a CANCELLED tombstone (not an empty list).
        assertEquals(WorkInfo.State.CANCELLED, infos.first().state)
    }

    @Test
    fun dmPollWorker_isBuiltByHiltFactory_andRunsToSuccess() {
        val worker =
            TestListenableWorkerBuilder<DmPollWorker>(context)
                .setWorkerFactory(workerFactory)
                .build()

        // No session in the test process → the runner's signed-in gate returns
        // SUCCESS without any network. This proves the @HiltWorker DI path
        // (HiltWorkerFactory → DmPollWorker → DmPollRunner) is wired correctly.
        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private companion object {
        // Mirrors WorkManagerDmWorkScheduler.UNIQUE_WORK_NAME (private there).
        const val UNIQUE_WORK_NAME = "dm-poll"
    }
}
