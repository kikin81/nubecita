package net.kikin.nubecita.core.wearsync

import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Clock

/**
 * Asks for a fresh feed from the network. Implemented by `:core:widget-sync`
 * (which owns the refresh worker and already depends on this module) by
 * enqueuing its existing one-time refresh, so the watch adds no new worker
 * and no new token-refresh caller.
 */
interface WearFeedRefreshTrigger {
    suspend fun refreshFeedsNow()
}

/**
 * Answers the watch's "I just opened" message (nubecita-jh9c.5):
 *
 * 1. republish the session, urgently;
 * 2. republish the selected feeds from the cache, urgently, so the watch has
 *    the newest stored posts within a second or so;
 * 3. ask for a network refresh (the existing one-time widget refresh, which
 *    publishes again when it finishes), at most once per [REFRESH_COOLDOWN_MS]
 *    so opening the watch app repeatedly doesn't refetch every time.
 *
 * Each step is isolated: one failing doesn't skip the next.
 */
class WearSyncRequestHandler internal constructor(
    private val sessionPublisher: WearSessionPublisher,
    private val sessionStateProvider: SessionStateProvider,
    private val feedSync: WearFeedSync,
    private val refreshTrigger: WearFeedRefreshTrigger,
    private val clock: Clock,
) {
    private val lastRefreshMs = AtomicLong(Long.MIN_VALUE)

    suspend fun onSyncRequest() {
        sessionPublisher.republish()
        val did = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did ?: return
        guarded("feed republish") { feedSync.publish(did, urgent = true) }

        val now = clock.now().toEpochMilliseconds()
        val last = lastRefreshMs.get()
        if (last != Long.MIN_VALUE && now - last < REFRESH_COOLDOWN_MS) return
        if (lastRefreshMs.compareAndSet(last, now)) guarded("feed refresh") { refreshTrigger.refreshFeedsNow() }
    }

    private suspend fun guarded(
        what: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.w(error, "watch sync request: %s failed", what)
        }
    }

    internal companion object {
        /** Opening the watch app more often than this republishes from the cache only. */
        const val REFRESH_COOLDOWN_MS = 5 * 60_000L
    }
}
