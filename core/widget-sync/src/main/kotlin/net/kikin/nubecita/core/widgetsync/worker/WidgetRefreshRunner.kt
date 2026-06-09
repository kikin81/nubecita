package net.kikin.nubecita.core.widgetsync.worker

import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import javax.inject.Inject

/**
 * The background widget-refresh orchestration (D-B4/D-B6/D-B7), extracted from
 * [WidgetRefreshWorker] as a plain injectable so it's JVM-unit-testable without
 * WorkManager — the worker's `doWork()` is a one-line delegate. Mirrors
 * `:feature:chats:impl`'s `DmPollRunner`.
 *
 * Per run, in order:
 * 1. **Signed-out gate** — no-op [Outcome.SUCCESS]: no session means nothing to
 *    refresh.
 * 2. **Foreground guard (D-B4)** — if the app is foregrounded, return
 *    [Outcome.SUCCESS] WITHOUT writing. A cache write would invalidate the app's
 *    own feed `PagingSource` (Room's table-level `InvalidationTracker`); while
 *    foregrounded the app's `RemoteMediator` is the writer, so the worker stays
 *    out of the way. (Coarse: process-wide, so we also skip while foregrounded on
 *    a non-feed screen — accepted in D-B4.)
 * 3. **Per-feed refresh (D-B6/D-B7)** — refresh the MVP feeds (Following +
 *    Discover) for the signed-in DID **independently**, and `trimToCap` each one
 *    that succeeds. Return [Outcome.RETRY] **only when *every* feed failed**; a
 *    partial or full success → [Outcome.SUCCESS], so a feed that already
 *    refreshed is never re-fetched by a WorkManager retry (which only fires when
 *    all failed). The failed feed in a partial-success run is picked up by the
 *    next periodic run.
 * 4. **Widget re-render** — call [WidgetUpdater.updateFeedWidgets] if at least
 *    one feed succeeded (no-op until sub-project C swaps in the Glance impl).
 *
 * Auth: all network goes through [FeedRepository], whose `:core:feed-cache`
 * refresh path uses the `:core:auth` refresh-mutex `XrpcClientProvider`, so a
 * background refresh can't race the foreground app into a logout.
 */
internal class WidgetRefreshRunner
    @Inject
    constructor(
        private val repository: FeedRepository,
        private val sessionStateProvider: SessionStateProvider,
        private val foreground: AppForegroundSignal,
        private val widgetUpdater: WidgetUpdater,
    ) {
        enum class Outcome { SUCCESS, RETRY }

        suspend fun run(): Outcome {
            val viewerDid =
                (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    ?: return Outcome.SUCCESS

            // D-B4: never write while foregrounded — the app's own RemoteMediator
            // is the writer, and a write would invalidate its active PagingSource.
            if (foreground.isForegrounded()) return Outcome.SUCCESS

            val feedKeys =
                listOf(
                    FeedKey.following(viewerDid),
                    FeedKey(viewerDid, FeedType.DISCOVER, DISCOVER_FEED_URI),
                )

            var anySucceeded = false
            var allFailed = true
            for (feedKey in feedKeys) {
                val refreshed = repository.refresh(feedKey).isSuccess
                if (refreshed) {
                    anySucceeded = true
                    allFailed = false
                    // Off-scroll eviction (D-B6): only after a feed's own refresh
                    // succeeds, so we never trim a partition we couldn't refresh.
                    repository.trimToCap(feedKey)
                }
            }

            // D-B7: RETRY only when EVERY feed failed; partial/full success → SUCCESS
            // (a succeeded feed must not be re-fetched by a retry).
            if (allFailed) return Outcome.RETRY

            if (anySucceeded) widgetUpdater.updateFeedWidgets()
            return Outcome.SUCCESS
        }

        private companion object {
            /**
             * The Bluesky Discover / "what's-hot" generator AT-URI (the fixed MVP
             * Discover partition, D-B6). C confirms the canonical value; the runner
             * logic and its tests don't depend on the exact string.
             */
            const val DISCOVER_FEED_URI =
                "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/whats-hot"
        }
    }
