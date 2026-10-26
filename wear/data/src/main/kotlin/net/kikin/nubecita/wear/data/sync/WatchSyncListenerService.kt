package net.kikin.nubecita.wear.data.sync

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/**
 * Stores what the phone syncs, even while the watch app is closed. The
 * manifest filter wakes it only for Nubecita's `/v1/` items.
 */
@AndroidEntryPoint
internal class WatchSyncListenerService : WearableListenerService() {
    @Inject
    lateinit var ingester: WatchSyncIngester

    override fun onDataChanged(events: DataEventBuffer) {
        // Freeze first: the buffer is only valid during this callback.
        val frozen = events.map { it.type to it.dataItem.freeze() }
        // A Play services background thread; blocking keeps the process alive
        // until the store is written.
        runBlocking {
            frozen.forEach { (type, item) ->
                when (type) {
                    DataEvent.TYPE_CHANGED -> applyItem(this@WatchSyncListenerService, ingester, item)
                    DataEvent.TYPE_DELETED -> item.uri.path?.let { applyDeletion(ingester, it) }
                }
            }
        }
    }
}
