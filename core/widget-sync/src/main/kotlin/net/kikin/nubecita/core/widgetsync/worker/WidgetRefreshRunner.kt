package net.kikin.nubecita.core.widgetsync.worker

import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.widgetsync.WidgetFeeds
import net.kikin.nubecita.core.widgetsync.WidgetImagePrefetcher
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import timber.log.Timber
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
 *    that succeeds, then prefetch that feed's head thumbnails via
 *    [WidgetImagePrefetcher] (D-C4) off the scroll path — a prefetch failure is
 *    isolated to that feed's images and never changes the feed's outcome. Return
 *    [Outcome.RETRY] **only when *every* feed failed**; a partial or full success
 *    → [Outcome.SUCCESS], so a feed that already refreshed is never re-fetched by
 *    a WorkManager retry (which only fires when all failed). The failed feed in a
 *    partial-success run is picked up by the next periodic run.
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
        private val imagePrefetcher: WidgetImagePrefetcher,
    ) {
        enum class Outcome { SUCCESS, RETRY }

        suspend fun run(): Outcome {
            // The worker can run in a cold process where the session StateFlow is
            // still at its initial Loading value (only MainActivity refreshes it on
            // app cold start). Without this, a backgrounded refresh would read
            // Loading, treat it as signed-out, and never populate the cache.
            // refresh() does disk I/O; on a storage error return SUCCESS (don't
            // retry-loop on corruption). Rethrow CancellationException.
            try {
                sessionStateProvider.refresh()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Timber.tag(TAG).w(throwable, "session refresh failed")
                return Outcome.SUCCESS
            }
            // SignedOut or Loading (identity not hydrated yet) → nothing to refresh
            // this run; the next periodic run retries.
            val viewerDid =
                (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    ?: return Outcome.SUCCESS

            // D-B4: never write while foregrounded — the app's own RemoteMediator
            // is the writer, and a write would invalidate its active PagingSource.
            if (foreground.isForegrounded()) return Outcome.SUCCESS

            val feedKeys =
                listOf(
                    FeedKey.following(viewerDid),
                    FeedKey(viewerDid, FeedType.DISCOVER, WidgetFeeds.DISCOVER_FEED_URI),
                )

            var anySucceeded = false
            var allFailed = true
            for (feedKey in feedKeys) {
                // Each feed is independent: an unexpected throw (a DB constraint,
                // a serialization error in trimToCap, etc. — `refresh` itself
                // returns a Result for network failures) must fail only THIS feed,
                // not abort the others. Rethrow CancellationException.
                try {
                    val refreshed = repository.refresh(feedKey).isSuccess
                    if (refreshed) {
                        anySucceeded = true
                        allFailed = false
                        // Off-scroll eviction (D-B6): only after a feed's own refresh
                        // succeeds, so we never trim a partition we couldn't refresh.
                        repository.trimToCap(feedKey)
                        // Off-scroll image prefetch (D-C4): decode this feed's head
                        // thumbnails for the widget. A prefetch failure must fail ONLY
                        // this feed's images — the refresh already succeeded, so it must
                        // not flip the outcome, skip the updater, or abort other feeds.
                        // Its own try/catch keeps that isolation precise (and the log
                        // accurate). Rethrow CancellationException.
                        try {
                            imagePrefetcher.prefetch(feedKey)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (throwable: Throwable) {
                            // Log only the feed type — the full FeedKey embeds the
                            // account DID + feed URI, which now reach Crashlytics breadcrumbs.
                            Timber.tag(TAG).w(throwable, "widget image prefetch failed: %s", feedKey.feedType)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "widget feed refresh failed: %s", feedKey.feedType)
                }
            }

            // D-B7: RETRY only when EVERY feed failed; partial/full success → SUCCESS
            // (a succeeded feed must not be re-fetched by a retry).
            if (allFailed) return Outcome.RETRY

            if (anySucceeded) {
                // A widget-render failure (Glance / AppWidgetManager IPC) must NOT
                // trigger a WorkManager retry — the cache is already refreshed, so
                // a retry would waste network/battery re-fetching. Log and succeed.
                try {
                    widgetUpdater.updateFeedWidgets()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "widget update failed")
                }
            }
            return Outcome.SUCCESS
        }

        private companion object {
            const val TAG = "WidgetRefreshRunner"
        }
    }
