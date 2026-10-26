package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.wearcontract.WearAccount
import net.kikin.nubecita.core.wearcontract.WearCodec
import net.kikin.nubecita.core.wearcontract.WearPaths
import net.kikin.nubecita.core.wearcontract.WearSessionSnapshot
import net.kikin.nubecita.core.wearsync.internal.WearDataLayer
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Clock

/**
 * Keeps the watch's copy of the phone session ([WearPaths.SESSION]) in step
 * with [SessionStateProvider].
 *
 * - **Signed in:** publishes the account, but only while a node has the Wear
 *   app — no watch, no snapshot.
 * - **Signed out:** always deletes everything under [WearPaths.PREFIX] (every
 *   account's snapshots and queued actions) and publishes a signed-out session,
 *   watch or no watch. Clearing is a local Data Layer write that syncs when a
 *   watch next connects, so a watch that was away during sign-out still wipes
 *   its copy instead of showing the previous account.
 *
 * Periodic changes are published non-urgent; [republish] (the watch app just
 * opened and is waiting) is urgent. Started once from the production-flavor
 * `AppInitializer`; the bench flavor never starts it.
 */
class WearSessionPublisher internal constructor(
    private val scope: CoroutineScope,
    private val sessionStateProvider: SessionStateProvider,
    private val dataLayer: WearDataLayer,
    private val clock: Clock,
) {
    private val started = AtomicBoolean(false)

    /** Idempotent — later calls short-circuit. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            // StateFlow already drops consecutive equal states.
            sessionStateProvider.state.collect { state -> publishGuarded(state, urgent = false) }
        }
    }

    /** Republishes the current session urgently, for a watch app that just opened. */
    suspend fun republish() {
        publishGuarded(sessionStateProvider.state.value, urgent = true)
    }

    private suspend fun publishGuarded(
        state: SessionState,
        urgent: Boolean,
    ) {
        // A Data Layer failure (Play services missing/updating) must not end the
        // collect, or the watch would stop following sign-in/out for the rest of
        // the process. Rethrow cancellation to keep it cooperative.
        try {
            publish(state, urgent)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Timber.tag(TAG).w(throwable, "wear session publish failed (state=%s)", state::class.simpleName)
        }
    }

    private suspend fun publish(
        state: SessionState,
        urgent: Boolean,
    ) {
        when (state) {
            SessionState.Loading -> Unit
            SessionState.SignedOut -> {
                dataLayer.deleteUnder(WearPaths.PREFIX)
                dataLayer.put(WearPaths.SESSION, encode(account = null), urgent)
            }
            is SessionState.SignedIn -> {
                if (!dataLayer.hasWearAppNode()) return
                dataLayer.put(WearPaths.SESSION, encode(WearAccount(did = state.did, handle = state.handle)), urgent)
            }
        }
    }

    private fun encode(account: WearAccount?): String =
        WearCodec.encode(
            WearSessionSnapshot.serializer(),
            WearSessionSnapshot(publishedAtMs = clock.now().toEpochMilliseconds(), account = account),
        )

    private companion object {
        const val TAG = "WearSessionPublisher"
    }
}
