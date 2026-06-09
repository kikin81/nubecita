package net.kikin.nubecita.core.widgetsync.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that refreshes the widget's feed cache off-app (D-B2). A
 * one-line delegate to [WidgetRefreshRunner] — all logic and its unit tests live
 * there; this class only adapts the runner's outcome to a WorkManager [Result].
 * Mirrors `:feature:chats:impl`'s `DmPollWorker`.
 *
 * Built by Hilt's `HiltWorkerFactory` (the same `Configuration.Provider` that
 * builds the DM worker, wired in `:app`).
 */
@HiltWorker
internal class WidgetRefreshWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val runner: WidgetRefreshRunner,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result =
            when (runner.run()) {
                WidgetRefreshRunner.Outcome.SUCCESS -> Result.success()
                WidgetRefreshRunner.Outcome.RETRY -> Result.retry()
            }
    }
