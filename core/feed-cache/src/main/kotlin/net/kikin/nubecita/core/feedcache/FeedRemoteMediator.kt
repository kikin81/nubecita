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
        private val feedCatchUp: FeedCatchUp,
    ) : RemoteMediator<Int, CachedFeedRow>() {
        @AssistedFactory
        interface Factory {
            fun create(feedKey: FeedKey): FeedRemoteMediator
        }

        /**
         * Render the cached partition immediately when it already holds rows
         * AND the feed is one a catch-up can keep current; otherwise trigger
         * the initial REFRESH.
         *
         * A TTL / staleness refinement (refetch a cached-but-stale partition) is
         * intentionally DEFERRED to the refresh worker (sub-project B) — it owns
         * the off-scroll refresh schedule. We don't add a `fetched_at` column or
         * a freshness check here; the foundation only needs "cached ⇒ skip".
         */
        override suspend fun initialize(): InitializeAction {
            val cached = feedPostDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) != null
            if (!cached) return InitializeAction.LAUNCH_INITIAL_REFRESH
            // A feed that cannot bound a catch-up refreshes on open, which is
            // what it did before the cache existed (D14). Rendering its cached
            // partition instead would show stale algorithmic content with no
            // way to reach the newest entries short of a pull — and for these
            // feeds the destructive replace IS the behaviour readers expect,
            // since nothing persists a position for them.
            if (!feedKey.feedType.supportsSince) return InitializeAction.LAUNCH_INITIAL_REFRESH
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
            state: PagingState<Int, CachedFeedRow>,
        ): MediatorResult =
            when (loadType) {
                // PREPEND delegates to the shared FeedCatchUp — the same
                // additive write-through the background worker drives with no
                // Pager (D10). NOT_POSSIBLE reports end-of-pagination here:
                // Paging has no destructive fallback to offer, and the reader's
                // route to newer entries is pull-to-refresh.
                LoadType.PREPEND ->
                    feedCatchUp.catchUp(feedKey).fold(
                        onSuccess = { MediatorResult.Success(endOfPaginationReached = it != CatchUpOutcome.MORE_PENDING) },
                        onFailure = { MediatorResult.Error(it) },
                    )
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

        private companion object {
            /**
             * How many consecutive barren pages to follow in one `load()`.
             *
             * Mirrors the limit `DefaultFeedRepository` used, so a reader whose
             * filters empty several pages behaves as it did before the
             * projection moved.
             */
            const val MAX_TOP_UP_ROUNDS = 3
        }
    }
