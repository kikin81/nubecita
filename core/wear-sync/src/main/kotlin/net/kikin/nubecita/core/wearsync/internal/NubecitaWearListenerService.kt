package net.kikin.nubecita.core.wearsync.internal

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.WearSessionPublisher
import javax.inject.Inject

/**
 * Answers the watch's Data Layer messages. The manifest filter limits wakeups
 * to `/v1/sync/` paths.
 */
@AndroidEntryPoint
internal class NubecitaWearListenerService : WearableListenerService() {
    @Inject
    lateinit var sessionPublisher: WearSessionPublisher

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // The watch app opened: send the session now (urgent). Feed
            // republishing joins this in W2 (nubecita-jh9c.5). The callback runs
            // on a Play services background thread, and blocking it keeps the
            // service (and the process) alive until the write is handed off.
            WearPaths.SYNC_REQUEST -> runBlocking { sessionPublisher.republish() }
        }
    }
}
