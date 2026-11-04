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
import net.kikin.nubecita.core.database.model.CachedFeedEntry
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository

/**
 * Paging 3 [RemoteMediator] for one cached feed partition. Reverse-chronological,
 * so it only loads *older* posts on demand (APPEND) and refreshes the whole
 * partition on REFRESH; there is no PREPEND (no "newer than the top" load — a
 * fresh REFRESH replaces the partition instead).
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
    ) : RemoteMediator<Int, CachedFeedEntry>() {
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
            state: PagingState<Int, CachedFeedEntry>,
        ): MediatorResult =
            when (loadType) {
                // Reverse-chron: there is no "load newer" — a REFRESH replaces
                // the partition from the top instead.
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
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

        private suspend fun append(): MediatorResult {
            val storedCursor =
                remoteKeyDao.remoteKey(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)?.nextCursor
                    ?: return MediatorResult.Success(endOfPaginationReached = true)

            val result = networkSource.fetchPage(feedKey, cursor = storedCursor)
            val (page, nextCursor) = result.getOrElse { return MediatorResult.Error(it) }

            transactionRunner.run {
                // Compute the next free position inside the transaction so a
                // concurrent write can't race the position assignment.
                val base = (feedPostDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: -1) + 1
                val entities = page.mapIndexed { index, post -> post.toFeedPostEntity(feedKey, position = base + index) }
                // Posts first, for the same reason as the refresher: the read
                // path inner-joins, so an envelope without its post is invisible.
                cachedPostDao.upsert(page.flatMap { it.toCachedPosts(feedKey) })
                feedPostDao.upsert(entities)
                // Same transaction, and after the posts: the projection's
                // members reference them, and the chain merge rehydrates the
                // partition's tail from them.
                projectionWriter.writeAppend(feedKey, page)
                remoteKeyDao.upsert(
                    FeedRemoteKeyEntity(
                        accountDid = feedKey.accountDid,
                        feedType = feedKey.feedType.name,
                        feedUri = feedKey.feedUri,
                        nextCursor = nextCursor,
                    ),
                )
            }
            return MediatorResult.Success(endOfPaginationReached = nextCursor == null || page.isEmpty())
        }
    }
