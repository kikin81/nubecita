package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import javax.inject.Inject

/**
 * The REFRESH write-through for one cached feed partition, extracted as a shared
 * unit so both the [FeedRemoteMediator] (Paging's REFRESH branch) and the
 * [FeedRepository.refresh] entry point (the Glance-free background widget worker,
 * sub-project B — no `Pager`) drive identical logic.
 *
 * Extracting it here (rather than onto [FeedRepository]) avoids a
 * repository↔mediator dependency cycle: [DefaultFeedRepository] creates the
 * mediator via [FeedRemoteMediator.Factory], so the mediator must NOT depend on
 * [FeedRepository]. Both instead depend on this plain injectable.
 *
 * Fetch page 1 (null cursor) → map to entities at positions 0..n → in ONE
 * transaction clear the partition, upsert the page, and upsert the next-page
 * cursor. Returns `endOfPaginationReached` (a null next cursor or an empty page)
 * on success, or the network failure on `Result.failure`.
 */
internal class FeedRefresher
    @Inject
    constructor(
        private val networkSource: FeedNetworkSource,
        private val feedPostDao: FeedPostDao,
        private val remoteKeyDao: FeedRemoteKeyDao,
        private val transactionRunner: FeedCacheTransactionRunner,
    ) {
        /**
         * Refresh [feedKey]'s partition from the top. Returns
         * `Result.success(endOfPaginationReached)` on a completed write-through,
         * `Result.failure` on a network error (leaving the cache untouched).
         *
         * `CancellationException` propagates: [FeedNetworkSource.fetchPage]
         * rethrows it unwrapped (it never becomes a `Result.failure`), and
         * `getOrElse` only handles the failure value — so a cancelled fetch
         * escapes this method rather than being miscollapsed into a failure.
         */
        suspend fun refresh(feedKey: FeedKey): Result<Boolean> {
            val result = networkSource.fetchPage(feedKey, cursor = null)
            val (page, nextCursor) = result.getOrElse { return Result.failure(it) }

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
            return Result.success(nextCursor == null || page.isEmpty())
        }
    }
