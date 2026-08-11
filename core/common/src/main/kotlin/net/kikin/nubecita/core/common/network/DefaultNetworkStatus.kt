package net.kikin.nubecita.core.common.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [NetworkStatus] over `ConnectivityManager`'s default-network callback.
 *
 * The callback fires for capability changes as well as connect/disconnect, so
 * every branch re-reads `isActiveNetworkMetered` rather than inferring the
 * answer from the callback that happened to fire. That keeps one source of
 * truth and avoids the case where `onCapabilitiesChanged` reports a network
 * that is no longer the active one.
 *
 * `onLost` emits `true` (treat "no network" as expensive): with nothing
 * connected there is nothing to autoplay anyway, and guessing "unmetered"
 * would start playback the instant a cellular connection came back.
 *
 * `distinctUntilChanged` because capability callbacks are chatty — signal
 * strength alone can fire several per second, and each duplicate would
 * otherwise restart every collector downstream.
 *
 * **Shared, not cold.** A bare `callbackFlow` registers one
 * `NetworkCallback` per collector, and this is a `@Singleton` precisely
 * because there should be one. `shareIn` collapses N collectors onto a
 * single registration. `WhileSubscribed(5000)` unregisters 5s after the
 * last collector leaves — long enough to ride out a rotation, short
 * enough that backgrounding the app stops the callback; `replay = 1`
 * hands a late collector the known answer instead of making it wait for
 * the network to change.
 */
@Singleton
class DefaultNetworkStatus
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @ApplicationScope private val externalScope: CoroutineScope,
    ) : NetworkStatus {
        override val isMetered: Flow<Boolean> =
            callbackFlow {
                val connectivity = context.getSystemService(ConnectivityManager::class.java)
                if (connectivity == null) {
                    // No ConnectivityManager: assume metered so a wifi-only
                    // preference errs toward NOT spending the user's data.
                    trySend(true)
                    awaitClose { }
                    return@callbackFlow
                }

                fun emitCurrent() = trySend(connectivity.isActiveNetworkMetered)

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            emitCurrent()
                        }

                        override fun onLost(network: Network) {
                            trySend(true)
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            emitCurrent()
                        }
                    }

                emitCurrent()
                connectivity.registerDefaultNetworkCallback(callback)
                awaitClose { connectivity.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .conflate()
                .shareIn(
                    scope = externalScope,
                    started = SharingStarted.WhileSubscribed(stopTimeoutMillis = CALLBACK_LINGER_MS),
                    replay = 1,
                )

        private companion object {
            /**
             * How long the `NetworkCallback` stays registered after the last
             * collector leaves. Covers a configuration change without
             * re-registering, and is well short of any window where keeping a
             * system callback alive would matter for battery.
             */
            const val CALLBACK_LINGER_MS = 5_000L
        }
    }
