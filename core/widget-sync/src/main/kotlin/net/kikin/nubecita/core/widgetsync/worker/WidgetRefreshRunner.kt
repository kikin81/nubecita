package net.kikin.nubecita.core.widgetsync.worker

import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.CatchUpOutcome
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.wearsync.WearFeedSync
import net.kikin.nubecita.core.widgetsync.PlacedWidgets
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
 * 3. **Consumer gate (nubecita-kvh0)** — no-op [Outcome.SUCCESS] when no feed
 *    widget is placed AND no watch has the app: nothing would read the result,
 *    and skipping before any network means the run cannot rotate the OAuth
 *    refresh token.
 * 4. **Per-feed refresh (D-B6/D-B7/D10)** — refresh the MVP feeds (Following +
 *    Discover) for the signed-in DID **independently** — additively where a
 *    bounded prepend is possible, destructively where it is not — and
 *    `trimToCap` each one
 *    that succeeds, then prefetch that feed's head thumbnails via
 *    [WidgetImagePrefetcher] (D-C4) off the scroll path — a prefetch failure is
 *    isolated to that feed's images and never changes the feed's outcome. Return
 *    [Outcome.RETRY] **only when *every* feed failed**; a partial or full success
 *    → [Outcome.SUCCESS], so a feed that already refreshed is never re-fetched by
 *    a WorkManager retry (which only fires when all failed). The failed feed in a
 *    partial-success run is picked up by the next periodic run.
 * 5. **Watch sync** — when a paired watch has the Wear app, refresh its extra
 *    selected feeds and publish them to the watch ([WearFeedSync]). Isolated:
 *    a watch failure never changes the outcome.
 * 6. **Widget re-render** — call [WidgetUpdater.updateFeedWidgets] if at least
 *    one feed succeeded (no-op until sub-project C swaps in the Glance impl).
 *
 * Auth: all network goes through [FeedRepository], whose `:core:feed-cache`
 * refresh path uses the `:core:auth` refresh-mutex `XrpcClientProvider`, so a
 * background refresh can't race the foreground app into a logout.
 *
 * The write being additive is not an optimization (D10). This runs on a period
 * while the app is backgrounded, which is exactly the window in which a
 * reader's persisted scroll anchor matters and the only window the foreground
 * guard does not cover. `FeedRefresher.refresh` is `clearPartition` + insert
 * in one transaction, so left unchanged it would delete the reader's
 * scrollback, and the row their anchor points at, between sittings.
 */
internal class WidgetRefreshRunner
    @Inject
    constructor(
        private val repository: FeedRepository,
        private val sessionStateProvider: SessionStateProvider,
        private val foreground: AppForegroundSignal,
        private val placedWidgets: PlacedWidgets,
        private val widgetUpdater: WidgetUpdater,
        private val imagePrefetcher: WidgetImagePrefetcher,
        private val wearFeedSync: WearFeedSync,
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

            // Nothing consumes this run unless a widget is placed or a watch has
            // the app (nubecita-kvh0). Both checks are local, and they run BEFORE
            // any network on purpose: a run that reaches the network can rotate
            // the single-use OAuth refresh token, and a rotation whose response is
            // lost strands that token into a permanent logout (nubecita-09xt). A
            // signed-in user with neither surface used to pay two feed fetches
            // every 15 minutes, forever, for output nobody read.
            val widgetsPlaced = widgetsPlaced()
            if (!widgetsPlaced && !watchPresent()) {
                Timber.tag(TAG).d("no placed widget and no watch — skipping refresh")
                return Outcome.SUCCESS
            }

            // Only the widgets need the MVP pair; the watch's own selection is
            // refreshed by syncWatch below, which subtracts whatever ran here.
            val feedKeys =
                if (widgetsPlaced) {
                    listOf(
                        FeedKey.following(viewerDid),
                        FeedKey(viewerDid, FeedType.DISCOVER, WidgetFeeds.DISCOVER_FEED_URI),
                    )
                } else {
                    emptyList()
                }

            var anySucceeded = false
            var allFailed = true
            for (feedKey in feedKeys) {
                // Each feed is independent: an unexpected throw (a DB constraint,
                // a serialization error in trimToCap, etc. — `refresh` itself
                // returns a Result for network failures) must fail only THIS feed,
                // not abort the others. Rethrow CancellationException.
                try {
                    val refreshed = writeFeed(feedKey)
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
            // (a succeeded feed must not be re-fetched by a retry). An empty list
            // is a watch-only run — nothing failed, so it must not retry.
            if (feedKeys.isNotEmpty() && allFailed) return Outcome.RETRY

            syncWatch(viewerDid, alreadyRefreshed = feedKeys)

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

        /**
         * Writes one feed's newer content, additively where that is possible.
         *
         * Returns whether the partition now holds fresh content — the same
         * boolean the destructive path returned, so the caller's per-feed
         * independence, retry-on-total-failure and trim/prefetch sequencing are
         * untouched.
         *
         * The fallback is not optional. A catch-up reports
         * [CatchUpOutcome.NOT_POSSIBLE] for a cold partition, for a partition
         * written before a boundary cursor was recorded, and for a generator
         * feed whose XRPC method accepts no `since` — and it decides that from
         * local state, before any fetch, so the fallback costs no extra
         * request. Without it those partitions would never be written again and
         * the widget would freeze on whatever it last showed.
         */
        private suspend fun writeFeed(feedKey: FeedKey): Boolean {
            val outcome = repository.catchUp(feedKey)
            return when (outcome.getOrNull()) {
                // A caught-up or partly-filled partition is fresh at its head,
                // which is all the widget reads.
                CatchUpOutcome.CAUGHT_UP, CatchUpOutcome.MORE_PENDING -> true
                CatchUpOutcome.NOT_POSSIBLE -> repository.refresh(feedKey).isSuccess
                // A failed catch-up is a network failure, and the destructive
                // path would hit the same network. Retrying it here would
                // double this run's requests on a flaky connection and, worse,
                // clear a partition the reader is still in because a prepend
                // happened to fail.
                null -> false
            }
        }

        /**
         * Whether any feed widget is placed. A failure answers `true`: this gate
         * exists to skip work nobody consumes, so when we cannot tell, keep the
         * old behaviour and refresh rather than silently freezing a live widget.
         */
        private suspend fun widgetsPlaced(): Boolean =
            try {
                placedWidgets.anyPlaced()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.tag(TAG).w(exception, "placed-widget check failed — refreshing anyway")
                true
            }

        /**
         * Whether a watch has the app. A failure answers `true` for the same
         * reason [widgetsPlaced] does — the run proceeds and [syncWatch], which
         * is already isolated from the outcome, absorbs the failure again there.
         */
        private suspend fun watchPresent(): Boolean =
            try {
                wearFeedSync.hasWatch()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                Timber.tag(TAG).w(exception, "watch presence check failed — refreshing anyway")
                true
            }

        /**
         * Wear OS companion (nubecita-jh9c.2): refresh the watch's extra feeds
         * through the same cache + auth path (this run is already tagged
         * `RefreshCaller.Widget`, so no new token-refresh caller), then publish
         * the watch snapshots. A no-op without a paired watch. Watch failures
         * never change this run's outcome: the widget refresh already succeeded.
         */
        private suspend fun syncWatch(
            viewerDid: String,
            alreadyRefreshed: List<FeedKey>,
        ) {
            try {
                for (feedKey in wearFeedSync.feedKeysToRefresh(viewerDid) - alreadyRefreshed.toSet()) {
                    try {
                        // The same additive-first write: a watch feed is a
                        // cached partition like any other, and the app's reader
                        // may be sitting in it.
                        if (writeFeed(feedKey)) repository.trimToCap(feedKey)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        Timber.tag(TAG).w(throwable, "watch feed refresh failed: %s", feedKey.feedType)
                    }
                }
                wearFeedSync.publish(viewerDid)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Timber.tag(TAG).w(throwable, "watch sync failed")
            }
        }

        private companion object {
            const val TAG = "WidgetRefreshRunner"
        }
    }
