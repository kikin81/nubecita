package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.data.models.FeedItemUi

/**
 * Paging 3 [RemoteMediator] for one cached feed partition. Reverse-chronological:
 * APPEND loads older entries, PREPEND catches up on what arrived above the
 * cached head while the reader was away, and REFRESH replaces the partition
 * from the top — the one destructive path (D8).
 *
 * Constructed per [FeedKey] via [Factory]; the [FeedPostDao.pagingSource] read
 * path supplies the `PagingState`, while this mediator owns the network fetch +
 * write-through into the cache. Eviction ([trimToCap]) is deliberately NOT run
 * here (D-A5/D6) — trimming inside `load()` would delete rows out from under the
 * live `PagingSource` and invalidate it mid-scroll; the refresh worker
 * (sub-project B) calls `FeedRepository.trimToCap` off the scroll path instead.
 */
@OptIn(ExperimentalPagingApi::class)
internal class FeedRemoteMediator
    @AssistedInject
    constructor(
        @Assisted private val feedKey: FeedKey,
        private val networkSource: FeedNetworkSource,
        private val feedPostDao: FeedPostDao,
        private val cachedPostDao: CachedPostDao,
        private val projectionWriter: FeedProjectionWriter,
        private val partitionStateDao: FeedPartitionStateDao,
        private val moderationPreferences: ModerationPreferencesRepository,
        private val feedViewPreferences: FeedViewPreferencesRepository,
        private val remoteKeyDao: FeedRemoteKeyDao,
        private val transactionRunner: FeedCacheTransactionRunner,
        private val feedRefresher: FeedRefresher,
    ) : RemoteMediator<Int, FeedItemUi>() {
        @AssistedFactory
        interface Factory {
            fun create(feedKey: FeedKey): FeedRemoteMediator
        }

        /**
         * Render the cached partition immediately when it already holds rows
         * (no auto-refetch on open/switch), else trigger the initial REFRESH.
         *
         * A TTL / staleness refinement (refetch a cached-but-stale partition) is
         * intentionally DEFERRED to the refresh worker (sub-project B) — it owns
         * the off-scroll refresh schedule. We don't add a `fetched_at` column or
         * a freshness check here; the foundation only needs "cached ⇒ skip".
         */
        override suspend fun initialize(): InitializeAction {
            val cached = feedPostDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) != null
            if (!cached) return InitializeAction.LAUNCH_INITIAL_REFRESH
            // A cached partition is still stale if the viewer's preferences have
            // moved since its projection was computed: the stored rows would
            // keep showing replies the reader has just asked to hide, and the
            // setting would appear to do nothing.
            //
            // Re-projected locally rather than refetched, which is the whole
            // reason the envelope carries its wire context: the preference
            // applies and the reader keeps their place. A refetch would apply
            // it just as correctly and lose the position this cache exists for.
            if (projectionIsStale()) {
                transactionRunner.run { projectionWriter.reproject(feedKey) }
            }
            return InitializeAction.SKIP_INITIAL_REFRESH
        }

        /**
         * Whether the partition's projection was built under different viewer
         * preferences than the current ones.
         *
         * An absent fingerprint counts as **not** stale: a partition written
         * before this column existed has no recorded preferences, and treating
         * that as a mismatch would refetch every partition once on upgrade for
         * no reader-visible benefit.
         *
         * A mismatch re-projects the partition locally (D22/D27). That is
         * possible only because the envelope persists the entry's own `reason`
         * and `reply`: the denormalized columns alone would feed the grouping
         * rules a reconstruction unlike what the server sent.
         */
        private suspend fun projectionIsStale(): Boolean {
            val stored =
                partitionStateDao
                    .state(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    ?.prefsFingerprint ?: return false
            val live =
                projectionPrefsFingerprint(
                    moderation = moderationPreferences.prefs.value,
                    feedView = feedViewPreferences.prefs.value,
                )
            return stored != live
        }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, FeedItemUi>,
        ): MediatorResult =
            when (loadType) {
                LoadType.PREPEND -> prepend()
                // REFRESH delegates to the shared FeedRefresher (the same
                // write-through the background widget worker uses). fetchPage
                // rethrows CancellationException unwrapped, so a cancelled load
                // propagates out of load() rather than being miscollapsed into
                // an Error — FeedRefresher preserves that shape.
                LoadType.REFRESH ->
                    feedRefresher.refresh(feedKey).fold(
                        onSuccess = { MediatorResult.Success(endOfPaginationReached = it) },
                        onFailure = { MediatorResult.Error(it) },
                    )
                LoadType.APPEND -> append()
            }

        /**
         * Catches up on the entries that arrived above the cached head while
         * the reader was away, without moving anything already stored.
         *
         * The range is bounded by the partition's own boundary cursor rather
         * than by elapsed time (D5): `getTimeline` returns the `since` value
         * back as its cursor once the range is exhausted, so "am I done" is a
         * fact the server states rather than a guess from how long the reader
         * was gone. A feed whose XRPC method has no `since` cannot bound a
         * range at all, so it reports end-of-pagination and the reader's only
         * route to newer entries stays the destructive refresh.
         *
         * Positions march **downward** through a window reserved below the
         * partition's current minimum (D17). The newest slice takes the most
         * negative end, each later slice the block beneath it, and nothing
         * already written is renumbered — which is the whole point, since
         * renumbering would invalidate the live `PagingSource` mid-scroll.
         *
         * The window is read from the stored rows, not tracked in memory, so a
         * fill interrupted by process death resumes **below** the pages that
         * already landed. Without that, a resumed fill would restart at the top
         * of the window and — because the write is an upsert keyed on
         * `position` — overwrite the batch above it rather than failing.
         *
         * One `load()` returns as soon as it has rendered items, exactly like
         * [append]: the rest of the range is fetched by the next PREPEND, which
         * resumes from the persisted fill cursor. A barren page is followed
         * under the same [MAX_TOP_UP_ROUNDS] budget for the same reason — the
         * reply tuners can empty a page, and Paging has no reason to ask again
         * if a load adds nothing.
         */
        private suspend fun prepend(): MediatorResult {
            if (!feedKey.feedType.supportsSince) return MediatorResult.Success(endOfPaginationReached = true)
            val key =
                remoteKeyDao.remoteKey(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
            // No boundary means no range: the partition predates a refresh that
            // recorded one. An unbounded fetch here would walk backwards
            // through the whole feed, so there is nothing to catch up on until
            // the next refresh records a boundary.
            val since = key.boundarySince ?: return MediatorResult.Success(endOfPaginationReached = true)

            var cursor = key.fillCursor
            val resuming = cursor != null
            var pendingBoundary = key.pendingBoundary
            val lowestPrepended =
                feedPostDao.maxNegativePosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            var nextFree =
                if (resuming && lowestPrepended != null) {
                    lowestPrepended + 1
                } else {
                    val min = feedPostDao.minPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: 0
                    minOf(min, 0) - CATCH_UP_ENTRY_BUDGET
                }

            var rounds = 0
            while (true) {
                rounds++
                // What is actually free below the next occupied row. The round
                // budget is per-load(), so a fill resumed often enough would
                // otherwise walk past its window and silently upsert over
                // stored entries; asking the database turns that into a stop.
                // A full window is the budget cap from D5 — the seam offers
                // jump-to-newest rather than a load-more nobody taps twice.
                val first =
                    feedPostDao.firstPositionAtOrAbove(
                        feedKey.accountDid,
                        feedKey.feedType.name,
                        feedKey.feedUri,
                        nextFree,
                    )
                val room = if (first == null) CATCH_UP_ENTRY_BUDGET else first - nextFree
                if (room <= 0) return MediatorResult.Success(endOfPaginationReached = true)

                val fetched =
                    networkSource
                        .fetchPage(
                            feedKey,
                            cursor = cursor,
                            since = since,
                            limit = minOf(FeedNetworkSource.DEFAULT_LIMIT, room),
                        ).getOrElse { return MediatorResult.Error(it) }
                val entries = fetched.posts
                // The newest page of a fresh fill carries the boundary the NEXT
                // catch-up will pass as `since`. A resumed fill already has one
                // recorded and must not take the one from a page deeper in the
                // range, which describes an older point in the feed.
                if (!resuming && rounds == 1) pendingBoundary = fetched.startCursor ?: pendingBoundary

                // The server states exhaustion by handing back the `since` it
                // was given. An empty page or a null cursor says the same.
                val exhausted = fetched.nextCursor == null || fetched.nextCursor == since || entries.isEmpty()
                val base = nextFree
                var projected = 0
                transactionRunner.run {
                    // Posts first: the read path inner-joins, so an envelope
                    // without its post is invisible.
                    cachedPostDao.upsert(entries.flatMap { it.toCachedPosts(feedKey) })
                    feedPostDao.upsert(
                        entries.mapIndexed { index, post -> post.toFeedPostEntity(feedKey, position = base + index) },
                    )
                    // The projection shares the slice the entries took: a block
                    // of items is never longer than the entries it came from.
                    projected = projectionWriter.writePrepend(feedKey, entries, basePosition = base)
                    // Targeted: `next_cursor` belongs to the other direction,
                    // and a whole-row write from here would clear it and break
                    // forward pagination with every one-directional test still
                    // passing. On exhaustion the pending boundary is promoted
                    // and the fill state cleared, so the next catch-up starts
                    // from this session's newest page rather than re-walking a
                    // range it has already seen.
                    remoteKeyDao.updatePrependCursors(
                        accountDid = feedKey.accountDid,
                        feedType = feedKey.feedType.name,
                        feedUri = feedKey.feedUri,
                        boundarySince = if (exhausted) pendingBoundary ?: since else since,
                        fillCursor = if (exhausted) null else fetched.nextCursor,
                        pendingBoundary = if (exhausted) null else pendingBoundary,
                    )
                }

                nextFree = base + entries.size
                if (projected > 0 || exhausted || rounds >= MAX_TOP_UP_ROUNDS) {
                    return MediatorResult.Success(endOfPaginationReached = exhausted)
                }
                cursor = fetched.nextCursor
            }
        }

        /**
         * Loads one page of older entries, topping up when a page projects to
         * nothing.
         *
         * A fetched page can legitimately produce **zero** rendered items: the
         * viewer's reply tuners can drop every entry in it, and
         * de-duplication can drop the rest. Reporting that as
         * `endOfPaginationReached` would stop the feed scrolling while the
         * server still has content, and reporting it as success-with-nothing
         * would leave Paging with no new items and no reason to ask again.
         *
         * So a barren page is followed, bounded by [MAX_TOP_UP_ROUNDS]. The
         * bound matters as much as the loop: an aggressively filtered account
         * could otherwise walk its whole timeline in one `load()`.
         *
         * This replaces the top-up loop in `DefaultFeedRepository`, which is
         * deleted with the screen migration. That one existed for the same
         * reason — filtering can empty a page — but sat above the cache, where
         * the projection it has to react to no longer happens.
         */
        private suspend fun append(): MediatorResult {
            var cursor =
                remoteKeyDao.remoteKey(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)?.nextCursor
                    ?: return MediatorResult.Success(endOfPaginationReached = true)

            var rounds = 0
            while (true) {
                rounds++
                val result = networkSource.fetchPage(feedKey, cursor = cursor)
                val (page, nextCursor) = result.getOrElse { return MediatorResult.Error(it) }

                var projected = 0
                transactionRunner.run {
                    // Compute the next free position inside the transaction so a
                    // concurrent write can't race the position assignment.
                    val base =
                        (feedPostDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: -1) + 1
                    val entities = page.mapIndexed { index, post -> post.toFeedPostEntity(feedKey, position = base + index) }
                    // Posts first, for the same reason as the refresher: the read
                    // path inner-joins, so an envelope without its post is invisible.
                    cachedPostDao.upsert(page.flatMap { it.toCachedPosts(feedKey) })
                    feedPostDao.upsert(entities)
                    // Same transaction, and after the posts: the projection's
                    // members reference them, and the chain merge rehydrates the
                    // partition's tail from them.
                    projected = projectionWriter.writeAppend(feedKey, page)
                    // A targeted update, not a whole-row write: the row also
                    // carries the prepend cursors, and upserting the whole thing
                    // from this direction would clear them.
                    remoteKeyDao.updateAppendCursor(
                        accountDid = feedKey.accountDid,
                        feedType = feedKey.feedType.name,
                        feedUri = feedKey.feedUri,
                        nextCursor = nextCursor,
                    )
                }

                val exhausted = nextCursor == null || page.isEmpty()
                if (projected > 0 || exhausted || rounds >= MAX_TOP_UP_ROUNDS) {
                    return MediatorResult.Success(endOfPaginationReached = exhausted)
                }
                // Barren page, more to come, budget left: follow the cursor.
                cursor = nextCursor
            }
        }

        internal companion object {
            /**
             * How many consecutive barren pages to follow in one `load()`.
             *
             * Mirrors the limit `DefaultFeedRepository` used, so a reader whose
             * filters empty several pages behaves as it did before the
             * projection moved.
             */
            const val MAX_TOP_UP_ROUNDS = 3

            /**
             * Entries one catch-up may write above the cached head.
             *
             * Denominated against the retention cap rather than chosen
             * independently (D19): the trim keeps the newest `cap` rows, so a
             * catch-up allowed to write `cap` entries would leave the next
             * worker run deleting the reader's whole unread backlog — the
             * feature eating itself at exactly the volume it was built for.
             * Half the cap bounds the window and leaves the backlog room.
             */
            const val CATCH_UP_ENTRY_BUDGET = FeedRepository.DEFAULT_CAP / 2
        }
    }
