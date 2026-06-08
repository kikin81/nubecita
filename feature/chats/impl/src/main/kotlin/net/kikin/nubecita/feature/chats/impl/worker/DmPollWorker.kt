package net.kikin.nubecita.feature.chats.impl.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic background worker that polls for new inbound DMs and posts local
 * notifications (v2, nubecita-1fy.15). A one-line delegate to [DmPollRunner] —
 * all logic and its unit tests live there; this class only adapts the runner's
 * outcome to a WorkManager [Result].
 *
 * Built by Hilt's `HiltWorkerFactory` (wired in §1). Not scheduled yet — the
 * `enqueueUniquePeriodicWork` registration is §7, so installing this PR changes
 * no runtime behavior.
 */
@HiltWorker
internal class DmPollWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val runner: DmPollRunner,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            when (runner.run()) {
                DmPollRunner.Outcome.SUCCESS -> Result.success()
                DmPollRunner.Outcome.RETRY -> Result.retry()
            }
    }
