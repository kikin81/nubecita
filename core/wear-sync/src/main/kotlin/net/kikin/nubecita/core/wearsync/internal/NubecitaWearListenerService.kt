package net.kikin.nubecita.core.wearsync.internal

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.wearcontract.WearDataKeys
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.WearActionDispatcher
import net.kikin.nubecita.core.wearsync.WearSyncRequestHandler
import javax.inject.Inject

/**
 * Answers the watch's Data Layer traffic. The manifest filters limit wakeups
 * to `/v1/sync/` messages and the watch's `/v1/<did>/action/<id>` items.
 */
@AndroidEntryPoint
internal class NubecitaWearListenerService : WearableListenerService() {
    @Inject
    lateinit var syncRequestHandler: WearSyncRequestHandler

    @Inject
    lateinit var actionDispatcher: WearActionDispatcher

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // The watch app opened: republish the session and feeds now (urgent)
            // and ask for a fresh feed. The callback runs on a Play services
            // background thread; blocking keeps the service (and the process)
            // alive until the writes are handed off.
            WearPaths.SYNC_REQUEST -> runBlocking { syncRequestHandler.onSyncRequest() }
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        // The buffer is only valid during this call, so read everything out
        // before running the actions. Deletions (including our own delete of a
        // handled action) carry nothing to run.
        val actions =
            events
                .filter { it.type == DataEvent.TYPE_CHANGED }
                .mapNotNull { event ->
                    val uri = event.dataItem.uri
                    val path = uri.path ?: return@mapNotNull null
                    val node = uri.host ?: return@mapNotNull null
                    if (WearPaths.parseAction(path) == null) return@mapNotNull null
                    Triple(node, path, DataMapItem.fromDataItem(event.dataItem).dataMap.getString(WearDataKeys.PAYLOAD))
                }
        // Blocking keeps the service alive until each result is written, like
        // the sync request above.
        runBlocking { actions.forEach { (node, path, json) -> actionDispatcher.onActionItem(node, path, json) } }
    }
}
