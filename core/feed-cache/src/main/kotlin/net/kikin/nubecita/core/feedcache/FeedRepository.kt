package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject

/**
 * Read + maintenance surface over the offline feed cache. Exposes a paged feed
 * (the app-feed read path, backed by a Room `PagingSource` + the
 * [FeedRemoteMediator]) plus two maintenance operations the refresh worker
 * (sub-project B) and the session observer drive off the active-scroll path.
 *
 * Only `:data:models` types cross this boundary — `FeedPostEntity` never leaks.
 */
interface FeedRepository {
    /**
     * The partition's rendered feed items, paged.
     *
     * Items, not posts: the grouping projection is computed when a page is
     * written, so this streams what the reader actually sees — singles,
     * clusters and chains — rather than a flat post list the consumer would
     * have to group again.
     *
     * [initialPosition] is the stored scroll anchor's position when there is
     * one: passing it as the `Pager`'s initial key means the first frame is
     * already where the reader left off, rather than being scrolled there once
     * it has drawn somewhere else.
     */
    fun pagedFeed(
        feedKey: FeedKey,
        initialPosition: Int? = null,
    ): Flow<PagingData<FeedItemUi>>

    /**
     * Stream of the newest (≤) [n] cached [PostUi] for [feedKey], ordered by
     * `position` (newest-first), re-mapped from the cached wire posts with the
     * *current* viewer DID and moderation prefs on each emission. This is the
     * **widget read path** — no Paging: Glance reads a small flat list.
     * Moderation-filtered posts (a resolved HIDE) are dropped, so the emitted
     * list may hold fewer than [n] items.
     */
    fun head(
        feedKey: FeedKey,
        n: Int,
    ): Flow<List<PostUi>>

    /**
     * Refresh [feedKey]'s partition from the top WITHOUT a `Pager`: fetch page 1
     * and transactionally replace the partition (clear + insert + next cursor).
     * This is the **background widget refresh path** (sub-project B) — the worker
     * drives a partition refresh off the active-scroll path. Returns
     * `Result.success(endOfPaginationReached)` (a null next cursor or empty page;
     * the worker ignores the boolean and cares only about success/failure), or
     * `Result.failure` on a network error, leaving the cache untouched.
     */
    suspend fun refresh(feedKey: FeedKey): Result<Boolean>

    /**
     * Evict everything beyond the newest [cap] posts in [feedKey]'s partition.
     * NOT called from the mediator's `load()` (D-A5) — invoked off the scroll
     * path by the refresh worker. Leaves the partition's cursor row intact.
     */
    suspend fun trimToCap(
        feedKey: FeedKey,
        cap: Int = DEFAULT_CAP,
    )

    /**
     * Purge all cached feed partitions (posts + cursors) for [accountDid].
     * Driven by the session observer on sign-out / account removal.
     */
    suspend fun clearAccount(accountDid: String)

    companion object {
        /** Per-partition retention cap (D-A5: ~500 newest posts). */
        const val DEFAULT_CAP = 500

        /** Paging page size for the app feed. */
        const val PAGE_SIZE = 25
    }
}

/**
 * Default [FeedRepository]. The read path maps `CachedFeedEntry → PostUi?` at map
 * time (so a later prefs/account change reflects on the next load), dropping the
 * nulls that moderation / malformed-blob produce. Reads `viewerDid` / `prefs`
 * via `.value` per item so the projection always reflects current state.
 */
@OptIn(ExperimentalPagingApi::class)
internal open class DefaultFeedRepository
    @Inject
    constructor(
        private val feedPostDao: FeedPostDao,
        private val cachedPostDao: CachedPostDao,
        private val feedItemDao: FeedItemDao,
        private val partitionStateDao: FeedPartitionStateDao,
        private val database: NubecitaDatabase,
        private val remoteKeyDao: FeedRemoteKeyDao,
        private val mediatorFactory: FeedRemoteMediator.Factory,
        private val sessionStateProvider: SessionStateProvider,
        private val moderationPreferences: ModerationPreferencesRepository,
        private val transactionRunner: FeedCacheTransactionRunner,
        private val feedRefresher: FeedRefresher,
    ) : FeedRepository {
        /**
         * The [RemoteMediator] for [feedKey]. Extracted as an `open` seam so a
         * JVM unit test can swap a no-op mediator (the assisted factory needs the
         * Hilt-generated impl, which a pure-JVM test can't construct).
         */
        protected open fun mediatorFor(feedKey: FeedKey): RemoteMediator<Int, FeedItemUi> = mediatorFactory.create(feedKey)

        override fun pagedFeed(
            feedKey: FeedKey,
            initialPosition: Int?,
        ): Flow<PagingData<FeedItemUi>> =
            Pager(
                config = PagingConfig(pageSize = FeedRepository.PAGE_SIZE),
                initialKey = initialPosition,
                remoteMediator = mediatorFor(feedKey),
                pagingSourceFactory = { pagingSourceFor(feedKey, initialPosition) },
            ).flow

        /**
         * The partition's read source. An `open` seam for the same reason
         * [mediatorFor] is one: a JVM test can serve fixed rows without a real
         * database, and the source itself is covered by its own tests.
         */
        protected open fun pagingSourceFor(
            feedKey: FeedKey,
            initialPosition: Int?,
        ): PagingSource<Int, FeedItemUi> =
            // A hand-rolled source keyed on `position`, not Room's generated
            // one. The generated source's refresh key is an offset, so any
            // write that adds rows above the reader moves it — see
            // FeedItemPagingSource.
            FeedItemPagingSource(
                feedKey = feedKey,
                feedItemDao = feedItemDao,
                cachedPostDao = cachedPostDao,
                database = database,
                initialPosition = initialPosition,
                // Read per load, not captured once: a later page, or a
                // preference change between pages, reflects the current state
                // without rebuilding the Pager.
                viewerDid = { (sessionStateProvider.state.value as? SessionState.SignedIn)?.did },
                prefs = { moderationPreferences.prefs.value },
            )

        override fun head(
            feedKey: FeedKey,
            n: Int,
        ): Flow<List<PostUi>> =
            combine(
                feedPostDao.head(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, n),
                sessionStateProvider.state,
                moderationPreferences.prefs,
            ) { entities, session, prefs ->
                // combine re-emits on ANY source change — the DB head, the
                // session, OR moderation prefs — so a mute/adult-pref toggle or
                // account change refreshes the widget list immediately, not only
                // on a DB write. (pagedFeed uses the per-item read instead because
                // PagingData can't be combined the same way.)
                val viewerDid = (session as? SessionState.SignedIn)?.did
                entities.mapNotNull { it.toPostUi(viewerDid, prefs) }
            }

        override suspend fun refresh(feedKey: FeedKey): Result<Boolean> = feedRefresher.refresh(feedKey)

        override suspend fun trimToCap(
            feedKey: FeedKey,
            cap: Int,
        ) {
            feedPostDao.trimToCap(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, cap)
        }

        override suspend fun clearAccount(accountDid: String) {
            transactionRunner.run {
                // All four grains, not just the entry table. The post store and
                // the projection hold the actual cached content, so clearing
                // only envelopes and cursors would leave a signed-out account's
                // posts readable on disk — which the `core-feed-cache` spec
                // forbids and which adding those tables silently introduced.
                feedPostDao.clearAccount(accountDid)
                feedItemDao.clearAccount(accountDid)
                cachedPostDao.clearAccount(accountDid)
                partitionStateDao.clearAccount(accountDid)
                remoteKeyDao.clearAccount(accountDid)
            }
        }
    }

/**
 * Tiny non-null carrier so a nullable [PostUi] can flow through `PagingData`
 * (whose element type is non-null) before the null-dropping `filter`.
 */
private data class PostUiOrNull(
    val value: PostUi?,
)
