package net.kikin.nubecita.feature.chats.impl.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Enqueues / cancels the periodic background DM-poll work. A seam over
 * [WorkManager] so [DmPollScheduler]'s schedule/cancel decision logic is
 * JVM-unit-testable; the real WorkManager wiring is exercised by the §9
 * `work-testing` instrumentation pass.
 *
 * `suspend` so the impl can offload WorkManager's (lazy) on-demand init + DB
 * touch off the caller's dispatcher — the scheduler collects on
 * `Dispatchers.Default` (CPU pool), which is the wrong place for disk I/O.
 */
internal interface DmWorkScheduler {
    suspend fun ensureScheduled()

    suspend fun cancel()
}

/**
 * Real [DmWorkScheduler] (design D1/D8): a unique [androidx.work.PeriodicWorkRequest]
 * at the platform-minimum 15-minute interval, requiring network connectivity,
 * with [ExistingPeriodicWorkPolicy.UPDATE] so a future change to the request
 * spec (interval / constraints) applies to existing installs in place, without
 * resetting the running schedule. **Battery-compliant**: no expedited work, no
 * foreground service, no wakelocks — Doze/App Standby batching is accepted.
 * WorkManager calls run on [IoDispatcher].
 */
internal class WorkManagerDmWorkScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : DmWorkScheduler {
        override suspend fun ensureScheduled() =
            withContext(ioDispatcher) {
                val request =
                    PeriodicWorkRequestBuilder<DmPollWorker>(POLL_INTERVAL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(
                            Constraints
                                .Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        ).build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
                Unit
            }

        override suspend fun cancel() =
            withContext(ioDispatcher) {
                WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
                Unit
            }

        private companion object {
            const val UNIQUE_WORK_NAME = "dm-poll"

            /** WorkManager's hard floor for periodic work. */
            const val POLL_INTERVAL_MINUTES = 15L
        }
    }
