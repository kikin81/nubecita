package net.kikin.nubecita.core.wearsync.internal

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.WearSyncRequestHandler
import javax.inject.Inject

/**
 * Answers the watch's Data Layer messages. The manifest filter limits wakeups
 * to `/v1/sync/` paths.
 */
@AndroidEntryPoint
internal class NubecitaWearListenerService : WearableListenerService() {
    @Inject
    lateinit var syncRequestHandler: WearSyncRequestHandler

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // The watch app opened: republish the session and feeds now (urgent)
            // and ask for a fresh feed. The callback runs on a Play services
            // background thread; blocking keeps the service (and the process)
            // alive until the writes are handed off.
            WearPaths.SYNC_REQUEST -> runBlocking { syncRequestHandler.onSyncRequest() }
        }
    }
}
