package net.kikin.nubecita.core.wearsync

import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearDecodeResult
import net.kikin.nubecita.core.wearcontract.WearDmSnapshot
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer

/**
 * Publishes the watch's DM snapshot (W4). `:feature:chats:impl` builds the
 * snapshot from its DM poll; this module only owns the Data Layer.
 */
interface WearDmPublisher {
    /** True when a known node (connected or not) has the Wear app. */
    suspend fun hasWatch(): Boolean

    /**
     * The snapshot last published for [accountDid], read back from the Data
     * Layer item itself. Items persist across process death, so threads that
     * didn't change keep their messages with no separate phone-side table.
     */
    suspend fun lastSnapshot(accountDid: String): WearDmSnapshot?

    /** Publishes non-urgently: DMs follow the 15-minute poll, not a tap. */
    suspend fun publish(snapshot: WearDmSnapshot)
}

internal class DataLayerWearDmPublisher(
    private val dataLayer: WearDataLayer,
) : WearDmPublisher {
    override suspend fun hasWatch(): Boolean = dataLayer.hasWearAppNode()

    override suspend fun lastSnapshot(accountDid: String): WearDmSnapshot? {
        val json = dataLayer.read(WearPaths.dms(accountDid)) ?: return null
        return (WearCodec.decode(WearDmSnapshot.serializer(), json) as? WearDecodeResult.Decoded)?.value
    }

    override suspend fun publish(snapshot: WearDmSnapshot) {
        dataLayer.put(WearPaths.dms(snapshot.accountDid), WearCodec.encode(WearDmSnapshot.serializer(), snapshot), urgent = false)
    }
}
