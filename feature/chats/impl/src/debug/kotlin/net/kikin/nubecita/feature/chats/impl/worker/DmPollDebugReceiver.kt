package net.kikin.nubecita.feature.chats.impl.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import timber.log.Timber

/**
 * DEBUG-ONLY trigger for a background DM poll, fired from adb:
 *
 *   adb shell am broadcast -n net.kikin.nubecita/net.kikin.nubecita.feature.chats.impl.worker.DmPollDebugReceiver
 *
 * Enqueues a one-time [DmPollWorker] rather than running [DmPollRunner] inline:
 * the real worker runs in the real (backgrounded) process via WorkManager, so
 * there's no `goAsync()` ANR window, no application-scope coroutine to babysit,
 * and the outcome shows in the normal "DmPoll" worker logs. Lives only in
 * `src/debug/`, so it never ships in release.
 */
internal class DmPollDebugReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        Timber.tag("DmPoll").d("Debug trigger -> enqueue one-time DmPollWorker")
        WorkManager
            .getInstance(context.applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<DmPollWorker>().build())
    }
}
