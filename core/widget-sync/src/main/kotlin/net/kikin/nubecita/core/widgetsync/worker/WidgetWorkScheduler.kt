package net.kikin.nubecita.core.widgetsync.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Enqueues / cancels the background widget-refresh work. A seam over
 * [WorkManager] so [WidgetRefreshScheduler]'s schedule/cancel decision logic is
 * JVM-unit-testable; the real WorkManager wiring is exercised by the
 * `work-testing` instrumentation pass (§8). Mirrors `:feature:chats:impl`'s
 * `DmWorkScheduler`.
 *
 * `suspend` so the impl can offload WorkManager's (lazy) on-demand init + DB
 * touch off the caller's dispatcher.
 */
internal interface WidgetWorkScheduler {
    /** Register the periodic refresh (idempotent — unique work). */
    suspend fun ensureScheduled()

    /** Enqueue a one-time refresh now (widget add / manual refresh — invoked by C). */
    suspend fun refreshNow()

    /** Cancel the periodic refresh. */
    suspend fun cancel()
}

/**
 * Real [WidgetWorkScheduler] (D-B3): a unique [androidx.work.PeriodicWorkRequest]
 * at the platform-minimum 15-minute interval requiring network connectivity,
 * with [ExistingPeriodicWorkPolicy.UPDATE] so a future change to the request spec
 * applies to existing installs without resetting the running schedule; plus a
 * unique one-time [androidx.work.OneTimeWorkRequest] for on-demand refreshes with
 * [ExistingWorkPolicy.KEEP] to suppress duplicate in-flight refreshes.
 * **Battery-compliant**: no expedited work, no foreground service, no wakelocks —
 * Doze / App Standby batching is accepted. WorkManager calls run on
 * [IoDispatcher].
 */
internal class WorkManagerWidgetWorkScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WidgetWorkScheduler {
        override suspend fun ensureScheduled() =
            withContext(ioDispatcher) {
                val request =
                    PeriodicWorkRequestBuilder<WidgetRefreshWorker>(REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(networkConstraints())
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
                Unit
            }

        override suspend fun refreshNow() =
            withContext(ioDispatcher) {
                val request =
                    OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                        .setConstraints(networkConstraints())
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
                Unit
            }

        override suspend fun cancel() =
            withContext(ioDispatcher) {
                WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
                Unit
            }

        private fun networkConstraints(): Constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

        private companion object {
            const val PERIODIC_WORK_NAME = "widget-feed-refresh-periodic"
            const val ONE_TIME_WORK_NAME = "widget-feed-refresh-now"

            /** WorkManager's hard floor for periodic work. */
            const val REFRESH_INTERVAL_MINUTES = 15L
        }
    }
