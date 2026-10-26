package net.kikin.nubecita.core.wearsync.internal

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
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

    /** Writes [json] as the item at [path]. [urgent] skips the system's delivery batching. */
    suspend fun put(
        path: String,
        json: String,
        urgent: Boolean,
    )

    /** Deletes every item whose path starts with [pathPrefix]. */
    suspend fun deleteUnder(pathPrefix: String)
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
        ) {
            val request = PutDataMapRequest.create(path).apply { dataMap.putString(WearDataKeys.PAYLOAD, json) }.asPutDataRequest()
            if (urgent) request.setUrgent()
            Wearable.getDataClient(context).putDataItem(request).await()
        }

        override suspend fun deleteUnder(pathPrefix: String) {
            // Host "*" matches the items this node wrote for every peer.
            val uri =
                Uri
                    .Builder()
                    .scheme(PutDataRequest.WEAR_URI_SCHEME)
                    .authority("*")
                    .path(pathPrefix)
                    .build()
            Wearable.getDataClient(context).deleteDataItems(uri, DataClient.FILTER_PREFIX).await()
        }
    }
