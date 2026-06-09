package net.kikin.nubecita.core.feedcache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Purges an account's cached feed partitions when it signs out. Observes
 * [SessionStateProvider.state] and, on the transition *away from* a
 * [SessionState.SignedIn], calls [FeedRepository.clearAccount] for the DID that
 * just left — so a logged-out account's cached feeds (and the next account's
 * cold cache) never leak across the session boundary.
 *
 * Mirrors `:feature:chats:impl`'s `DmPollScheduler`: a plain class with a
 * `start()` that launches one `collect` on an injected app-scoped
 * [CoroutineScope], JVM-unit-testable against a fake [FeedRepository] +
 * [SessionStateProvider]. The actual `start()` wiring at the `:app` layer lands
 * when the cache is consumed (sub-project E) / by the refresh worker
 * (sub-project B) — not in this foundation PR.
 */
internal class FeedCacheEvictionCoordinator(
    private val scope: CoroutineScope,
    private val sessionStateProvider: SessionStateProvider,
    private val feedRepository: FeedRepository,
) {
    private val started = AtomicBoolean(false)

    /** Idempotent — subsequent calls short-circuit. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            var previousDid: String? = null
            sessionStateProvider.state
                .collect { session ->
                    val currentDid = (session as? SessionState.SignedIn)?.did
                    // Transition out of a signed-in account (sign-out or account
                    // switch): purge the account that just left. Loading →
                    // SignedOut at cold start leaves previousDid null → no-op.
                    if (previousDid != null && previousDid != currentDid) {
                        val leaving = requireNotNull(previousDid)
                        try {
                            feedRepository.clearAccount(leaving)
                        } catch (cancellation: CancellationException) {
                            // Let scope cancellation propagate — don't log it as
                            // a clearAccount failure or swallow the cancel.
                            throw cancellation
                        } catch (throwable: Throwable) {
                            Timber.tag(TAG).e(throwable, "clearAccount(%s) failed", leaving)
                        }
                    }
                    previousDid = currentDid
                }
        }
    }

    private companion object {
        const val TAG = "FeedCacheEviction"
    }
}
