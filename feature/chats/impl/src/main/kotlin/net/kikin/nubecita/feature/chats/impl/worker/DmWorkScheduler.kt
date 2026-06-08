package net.kikin.nubecita.feature.chats.impl.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Enqueues / cancels the periodic background DM-poll work. A seam over
 * [WorkManager] so [DmPollScheduler]'s schedule/cancel decision logic is
 * JVM-unit-testable; the real WorkManager wiring is exercised by the §9
 * `work-testing` instrumentation pass.
 */
internal interface DmWorkScheduler {
    fun ensureScheduled()

    fun cancel()
}

/**
 * Real [DmWorkScheduler] (design D1/D8): a unique [androidx.work.PeriodicWorkRequest]
 * at the platform-minimum 15-minute interval, requiring network connectivity,
 * with [ExistingPeriodicWorkPolicy.KEEP] so re-enqueueing never resets a
 * pending schedule. **Battery-compliant**: no expedited work, no foreground
 * service, no wakelocks — Doze/App Standby batching is accepted.
 */
internal class WorkManagerDmWorkScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : DmWorkScheduler {
        override fun ensureScheduled() {
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
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        override fun cancel() {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        private companion object {
            const val UNIQUE_WORK_NAME = "dm-poll"

            /** WorkManager's hard floor for periodic work. */
            const val POLL_INTERVAL_MINUTES = 15L
        }
    }
