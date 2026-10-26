package net.kikin.nubecita.wear.data.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.wearcontract.WearDataKeys
import net.kikin.nubecita.core.wearcontract.WearPaths
import timber.log.Timber
import javax.inject.Inject

/** Brings the offline store up to date with the Data Layer items already on the watch. */
interface WatchSync {
    /**
     * Re-applies every stored Nubecita item. Covers items that arrived while
     * the listener service couldn't run (first install, the app force-stopped);
     * the ingester skips snapshots it already has, so this is cheap.
     */
    suspend fun catchUp()
}

internal class DataLayerWatchSync
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val ingester: WatchSyncIngester,
    ) : WatchSync {
        override suspend fun catchUp() {
            val client = Wearable.getDataClient(context)
            val uri =
                Uri
                    .Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .authority("*")
                    .path(WearPaths.PREFIX)
                    .build()
            val buffer = client.getDataItems(uri, DataClient.FILTER_PREFIX).await()
            // Freeze before releasing the buffer; items are read after it closes.
            val items =
                try {
                    buffer.map { it.freeze() }
                } finally {
                    buffer.release()
                }
            // Session first: it decides which account's feeds are kept.
            val (sessions, others) = items.partition { it.uri.path == WearPaths.SESSION }
            if (sessions.isEmpty()) ingester.onSession(null)
            (sessions + others).forEach { applyItem(context, ingester, it) }
        }
    }

/** Applies one changed Data Layer item; shared by the listener and catch-up. */
internal suspend fun applyItem(
    context: Context,
    ingester: WatchSyncIngester,
    item: DataItem,
) {
    val path = item.uri.path ?: return
    try {
        val map = DataMapItem.fromDataItem(item).dataMap
        val json = map.getString(WearDataKeys.PAYLOAD) ?: return
        when {
            path == WearPaths.SESSION -> ingester.onSession(json)
            WearPaths.parseDms(path) != null -> ingester.ingestDms(path, json)
            WearPaths.parseActionResult(path) != null ->
                // Applied once: the watch deletes the phone's answer afterwards.
                if (ingester.onActionResult(path, json)) Wearable.getDataClient(context).deleteDataItems(item.uri).await()
            WearPaths.parseFeed(path) != null ->
                ingester.ingestFeed(path, json) { key ->
                    val asset = map.getAsset(key) ?: return@ingestFeed null
                    Wearable
                        .getDataClient(context)
                        .getFdForAsset(asset)
                        .await()
                        .inputStream
                        .use { it.readBytes() }
                }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        // One bad item must not stop the rest of a sync.
        Timber.w(error, "failed to apply a synced item")
    }
}

/** Applies a deleted Data Layer item. */
internal suspend fun applyDeletion(
    ingester: WatchSyncIngester,
    path: String,
) {
    when {
        path == WearPaths.SESSION -> ingester.onSession(null)
        WearPaths.parseDms(path) != null -> ingester.onDmsDeleted(path)
        WearPaths.parseFeed(path) != null -> ingester.onFeedDeleted(path)
    }
}
