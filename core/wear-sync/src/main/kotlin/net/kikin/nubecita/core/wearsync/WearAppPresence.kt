package net.kikin.nubecita.core.wearsync

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.wearcontract.WearCapabilities
import timber.log.Timber
import javax.inject.Inject

/** Whether any paired watch has the Nubecita Wear app installed. */
interface WearAppPresence {
    /**
     * Current answer first, then every change (a watch app installed or
     * removed, a watch paired or forgotten). Gates phone UI that only makes
     * sense with a watch, such as Settings → Watch.
     */
    val isWearAppInstalled: Flow<Boolean>
}

internal class PlayServicesWearAppPresence
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WearAppPresence {
        override val isWearAppInstalled: Flow<Boolean> =
            callbackFlow {
                val client = Wearable.getCapabilityClient(context)

                // FILTER_ALL, not REACHABLE: a watch that's out of Bluetooth range
                // still has the app, and the settings row shouldn't flicker away.
                suspend fun query(): Boolean =
                    try {
                        client
                            .getCapability(WearCapabilities.WEAR_APP, CapabilityClient.FILTER_ALL)
                            .await()
                            .nodes
                            .isNotEmpty()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Exception) {
                        // Play services missing or updating: treat as no watch.
                        Timber.w(error, "wear capability query failed")
                        false
                    }

                val listener = CapabilityClient.OnCapabilityChangedListener { launch { send(query()) } }
                client.addListener(listener, WearCapabilities.WEAR_APP)
                send(query())
                awaitClose { client.removeListener(listener) }
            }.distinctUntilChanged()
    }
