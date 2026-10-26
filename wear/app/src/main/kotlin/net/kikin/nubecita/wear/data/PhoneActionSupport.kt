package net.kikin.nubecita.wear.data

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.wearcontract.WearCapabilities
import javax.inject.Inject

/**
 * Whether the paired phone runs watch actions. A phone on an older build
 * doesn't advertise [WearCapabilities.PHONE_ACTIONS], so the watch hides its
 * like / repost / bookmark controls rather than queue actions nothing answers.
 */
interface PhoneActionSupport {
    suspend fun available(): Boolean
}

internal class CapabilityPhoneActionSupport
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PhoneActionSupport {
        // FILTER_ALL, not REACHABLE: actions queue while the phone is away.
        override suspend fun available(): Boolean =
            Wearable
                .getCapabilityClient(context)
                .getCapability(WearCapabilities.PHONE_ACTIONS, CapabilityClient.FILTER_ALL)
                .await()
                .nodes
                .isNotEmpty()
    }
