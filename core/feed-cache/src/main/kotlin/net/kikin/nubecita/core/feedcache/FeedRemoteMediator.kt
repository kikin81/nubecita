package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity

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
        private val remoteKeyDao: FeedRemoteKeyDao,
        private val transactionRunner: FeedCacheTransactionRunner,
    ) : RemoteMediator<Int, FeedPostEntity>() {
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
        override suspend fun initialize(): InitializeAction =
            if (feedPostDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) != null) {
                InitializeAction.SKIP_INITIAL_REFRESH
            } else {
                InitializeAction.LAUNCH_INITIAL_REFRESH
            }

        override suspend fun load(
            loadType: LoadType,
            state: PagingState<Int, FeedPostEntity>,
        ): MediatorResult =
            when (loadType) {
                // Reverse-chron: there is no "load newer" — a REFRESH replaces
                // the partition from the top instead.
                LoadType.PREPEND -> MediatorResult.Success(endOfPaginationReached = true)
                LoadType.REFRESH -> refresh()
                LoadType.APPEND -> append()
            }

        private suspend fun refresh(): MediatorResult {
            // fetchPage rethrows CancellationException unwrapped, so a cancelled
            // load propagates out of load() rather than being miscollapsed into
            // an Error — do NOT catch Throwable broadly here.
            val result = networkSource.fetchPage(feedKey, cursor = null)
            val (page, nextCursor) = result.getOrElse { return MediatorResult.Error(it) }

            val entities = page.mapIndexed { index, post -> post.toFeedPostEntity(feedKey, position = index) }
            transactionRunner.run {
                feedPostDao.clearPartition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                feedPostDao.upsert(entities)
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
                feedPostDao.upsert(entities)
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
