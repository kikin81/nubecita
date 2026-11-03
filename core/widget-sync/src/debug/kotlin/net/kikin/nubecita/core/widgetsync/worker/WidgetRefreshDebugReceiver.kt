package net.kikin.nubecita.core.widgetsync.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber

/**
 * DEBUG-ONLY trigger for a background widget refresh, fired from adb:
 *
 *   adb shell am broadcast -n net.kikin.nubecita/net.kikin.nubecita.core.widgetsync.worker.WidgetRefreshDebugReceiver
 *
 * The periodic schedule is only registered by the production flavor's
 * `AppInitializer`, and even there it runs on WorkManager's 15-minute floor, so
 * there was no way to exercise [WidgetRefreshRunner] on a device on demand —
 * including its consumer gate (nubecita-kvh0), whose whole job is to decide
 * whether this run touches the network at all.
 *
 * Enqueues a one-time [WidgetRefreshWorker] rather than running the runner
 * inline, so it executes in the real backgrounded process with no `goAsync()`
 * ANR window and reports through the normal "WidgetRefreshRunner" logs. Lives
 * only in `src/debug/`, so it never ships in release. Mirrors
 * `:feature:chats:impl`'s `DmPollDebugReceiver`.
 */
internal class WidgetRefreshDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Timber.tag("WidgetRefreshRunner").d("Debug trigger -> enqueue one-time WidgetRefreshWorker")
        WorkManager
            .getInstance(context.applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build())
    }
}
