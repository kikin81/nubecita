package net.kikin.nubecita.core.common.network

import kotlinx.coroutines.flow.Flow

/**
 * Whether the connection the device is currently using costs the user money.
 *
 * "Metered" rather than "is it wifi" on purpose: the OS's own answer
 * (`ConnectivityManager.isActiveNetworkMetered`) counts a metered wifi hotspot
 * as expensive and an unmetered cellular plan as cheap, which is what a user
 * choosing "only on wifi" actually means. Asking about the transport type
 * instead would get the tethering case backwards.
 *
 * A [Flow] rather than a one-shot check because the answer changes constantly
 * in ordinary use — walking out of the house drops wifi mid-scroll. That is the
 * opposite of `DataSaverStatus` in `:core:video`, which is deliberately checked
 * on demand because the system Data Saver toggle rarely moves during a session.
 * Do not "simplify" this to a suspend function; the whole point of a wifi-only
 * preference is that it stops applying the moment wifi does.
 */
interface NetworkStatus {
    /** Emits the current metered state, then again on every change. */
    val isMetered: Flow<Boolean>
}
