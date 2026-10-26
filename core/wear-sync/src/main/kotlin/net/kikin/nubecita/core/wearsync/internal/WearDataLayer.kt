package net.kikin.nubecita.core.wearsync.internal

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.wearcontract.WearCapabilities
import net.kikin.nubecita.core.wearcontract.WearDataKeys
import javax.inject.Inject

/**
 * Seam over the Wear Data Layer client so the publishing decisions are
 * JVM-unit-testable. The real implementation is a thin pass-through to Play
 * services Wearable.
 */
internal interface WearDataLayer {
    /** True when any known node (connected or not) has the Wear app installed. */
    suspend fun hasWearAppNode(): Boolean

    /**
     * Writes [json] as the item at [path], with [assets] (image bytes by key)
     * attached beside it. [urgent] skips the system's delivery batching.
     */
    suspend fun put(
        path: String,
        json: String,
        urgent: Boolean,
        assets: Map<String, ByteArray> = emptyMap(),
    )

    /** Deletes every item whose path starts with [pathPrefix]. */
    suspend fun deleteUnder(pathPrefix: String)

    /** Deletes the item at exactly [path] (never its prefix siblings). */
    suspend fun delete(path: String)

    /** Deletes the item at exactly [path] that the peer node [nodeId] wrote (e.g. a watch action). */
    suspend fun deleteFromNode(
        nodeId: String,
        path: String,
    )

    /** Paths of the items this node has written under [pathPrefix]. */
    suspend fun pathsUnder(pathPrefix: String): List<String>

    /** The JSON payload this node last wrote at exactly [path], or null when there is none. */
    suspend fun read(path: String): String?
}

internal class PlayServicesWearDataLayer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WearDataLayer {
        override suspend fun hasWearAppNode(): Boolean =
            Wearable
                .getCapabilityClient(context)
                .getCapability(WearCapabilities.WEAR_APP, CapabilityClient.FILTER_ALL)
                .await()
                .nodes
                .isNotEmpty()

        override suspend fun put(
            path: String,
            json: String,
            urgent: Boolean,
            assets: Map<String, ByteArray>,
        ) {
            val request =
                PutDataMapRequest
                    .create(path)
                    .apply {
                        dataMap.putString(WearDataKeys.PAYLOAD, json)
                        assets.forEach { (key, bytes) -> dataMap.putAsset(key, Asset.createFromBytes(bytes)) }
                    }.asPutDataRequest()
            if (urgent) request.setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }

        override suspend fun pathsUnder(pathPrefix: String): List<String> {
            val items = Wearable.getDataClient(context).getDataItems(wildcardUri(pathPrefix), DataClient.FILTER_PREFIX).await()
            return try {
                items.mapNotNull { it.uri.path }
            } finally {
                items.release()
            }
        }

        override suspend fun read(path: String): String? {
            val items = Wearable.getDataClient(context).getDataItems(wildcardUri(path), DataClient.FILTER_LITERAL).await()
            return try {
                items.firstOrNull()?.let { DataMapItem.fromDataItem(it).dataMap.getString(WearDataKeys.PAYLOAD) }
            } finally {
                items.release()
            }
        }

        override suspend fun deleteUnder(pathPrefix: String) {
            Wearable.getDataClient(context).deleteDataItems(wildcardUri(pathPrefix), DataClient.FILTER_PREFIX).await()
        }

        override suspend fun delete(path: String) {
            Wearable.getDataClient(context).deleteDataItems(wildcardUri(path), DataClient.FILTER_LITERAL).await()
        }

        override suspend fun deleteFromNode(
            nodeId: String,
            path: String,
        ) {
            Wearable.getDataClient(context).deleteDataItems(itemUri(nodeId, path), DataClient.FILTER_LITERAL).await()
        }

        // Host "*" matches the items this node wrote, whichever peer they sync to.
        private fun wildcardUri(path: String): Uri = itemUri("*", path)

        private fun itemUri(
            host: String,
            path: String,
        ): Uri =
            Uri
                .Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .authority(host)
                .path(path)
                .build()
    }
