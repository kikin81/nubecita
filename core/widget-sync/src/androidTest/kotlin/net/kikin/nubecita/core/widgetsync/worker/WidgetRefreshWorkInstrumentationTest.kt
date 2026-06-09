package net.kikin.nubecita.core.widgetsync.worker

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
 * `androidx.work:work-testing` coverage for the background widget-refresh worker
 * (§8). The pure orchestration ([WidgetRefreshRunner]) and the schedule/cancel
 * decision ([WidgetRefreshScheduler]) are exhaustively JVM-unit-tested; this
 * verifies the two pieces that need the Android runtime:
 * 1. [WorkManagerWidgetWorkScheduler] enqueues a unique **periodic** request with
 *    the `NetworkType.CONNECTED` constraint at the 15-minute floor (D-B3).
 * 2. [WidgetRefreshWorker] is constructable through Hilt's [HiltWorkerFactory]
 *    and its `doWork()` runs end-to-end. The test process has no session, so the
 *    runner's signed-out gate returns SUCCESS without touching the network —
 *    which also exercises a "backgrounded" path (no ProcessLifecycle STARTED).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WidgetRefreshWorkInstrumentationTest {
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
            WorkManagerWidgetWorkScheduler(context, Dispatchers.Unconfined).ensureScheduled()
        }

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PERIODIC_WORK_NAME).get()
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
    fun refreshNow_enqueuesUniqueOneTimeWorkWithNetworkConstraint() {
        runBlocking {
            WorkManagerWidgetWorkScheduler(context, Dispatchers.Unconfined).refreshNow()
        }

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(ONE_TIME_WORK_NAME).get()
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.first().constraints.requiredNetworkType)
    }

    @Test
    fun cancel_removesThePeriodicWork() {
        runBlocking {
            val scheduler = WorkManagerWidgetWorkScheduler(context, Dispatchers.Unconfined)
            scheduler.ensureScheduled()
            scheduler.cancel()
        }

        val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PERIODIC_WORK_NAME).get()
        assertEquals(1, infos.size)
        // Cancel leaves a CANCELLED tombstone (not an empty list).
        assertEquals(WorkInfo.State.CANCELLED, infos.first().state)
    }

    @Test
    fun widgetRefreshWorker_isBuiltByHiltFactory_andRunsToSuccess() {
        val worker =
            TestListenableWorkerBuilder<WidgetRefreshWorker>(context)
                .setWorkerFactory(workerFactory)
                .build()

        // No session in the test process → the runner's signed-in gate returns
        // SUCCESS without any network. This proves the @HiltWorker DI path
        // (HiltWorkerFactory → WidgetRefreshWorker → WidgetRefreshRunner) is wired.
        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private companion object {
        // Mirror WorkManagerWidgetWorkScheduler's (private) unique-work names.
        const val PERIODIC_WORK_NAME = "widget-feed-refresh-periodic"
        const val ONE_TIME_WORK_NAME = "widget-feed-refresh-now"
    }
}
