package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.database.dao.CachedPostDao
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
 * transaction clear the partition, upsert the page's posts and envelopes, and
 * upsert the next-page cursor. The partition clear leaves `cached_post` alone:
 * a post may still be referenced as another partition's reply context, so
 * eviction there is reference-counted rather than partition-scoped. Returns `endOfPaginationReached` (a null next cursor or an empty page)
 * on success, or the network failure on `Result.failure`.
 */
internal class FeedRefresher
    @Inject
    constructor(
        private val networkSource: FeedNetworkSource,
        private val feedPostDao: FeedPostDao,
        private val cachedPostDao: CachedPostDao,
        private val projectionWriter: FeedProjectionWriter,
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
            val fetched = result.getOrElse { return Result.failure(it) }
            val page = fetched.posts
            val nextCursor = fetched.nextCursor

            val entities = page.mapIndexed { index, post -> post.toFeedPostEntity(feedKey, position = index) }
            val posts = page.flatMap { it.toCachedPosts(feedKey) }
            transactionRunner.run {
                feedPostDao.clearPartition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                // Posts before envelopes: the read path inner-joins the two, so
                // an envelope whose post is not yet stored is invisible. Both
                // land in one transaction, so no reader observes the gap, but
                // the order keeps the invariant obvious rather than incidental.
                cachedPostDao.upsert(posts)
                feedPostDao.upsert(entities)
                // Inside the same transaction: a reader must never see a
                // partition whose projection disagrees with its entry rows.
                projectionWriter.writeRefresh(feedKey, page)
                remoteKeyDao.upsert(
                    FeedRemoteKeyEntity(
                        accountDid = feedKey.accountDid,
                        feedType = feedKey.feedType.name,
                        feedUri = feedKey.feedUri,
                        nextCursor = nextCursor,
                        // The boundary a later catch-up fetches from. A refresh
                        // replaces the partition, so this page IS the newest
                        // one ever written and its `startCursor` is the only
                        // `since` a prepend could correctly pass. Writing the
                        // whole row here also resets the in-flight fill
                        // cursors, which a destructive refresh must: the range
                        // they described no longer exists.
                        boundarySince = fetched.startCursor,
                    ),
                )
            }
            return Result.success(nextCursor == null || page.isEmpty())
        }
    }
