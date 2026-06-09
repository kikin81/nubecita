package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.data.models.PostUi
import javax.inject.Inject
import androidx.paging.filter as pagingFilter
import androidx.paging.map as pagingMap

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
     * Stream of paged [PostUi] for [feedKey], re-mapped from the cached wire
     * posts on each load with the *current* viewer DID and moderation prefs.
     * Moderation-filtered posts (a resolved HIDE) are dropped from the stream.
     */
    fun pagedFeed(feedKey: FeedKey): Flow<PagingData<PostUi>>

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
 * Default [FeedRepository]. The read path maps `FeedPostEntity → PostUi?` at map
 * time (so a later prefs/account change reflects on the next load), dropping the
 * nulls that moderation / malformed-blob produce. Reads `viewerDid` / `prefs`
 * via `.value` per item so the projection always reflects current state.
 */
@OptIn(ExperimentalPagingApi::class)
internal open class DefaultFeedRepository
    @Inject
    constructor(
        private val feedPostDao: FeedPostDao,
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
        protected open fun mediatorFor(feedKey: FeedKey): RemoteMediator<Int, FeedPostEntity> = mediatorFactory.create(feedKey)

        override fun pagedFeed(feedKey: FeedKey): Flow<PagingData<PostUi>> =
            Pager(
                config = PagingConfig(pageSize = FeedRepository.PAGE_SIZE),
                remoteMediator = mediatorFor(feedKey),
                pagingSourceFactory = {
                    feedPostDao.pagingSource(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                },
            ).flow
                .map { pagingData ->
                    // Read current viewer + prefs INSIDE the per-item map (not
                    // once per PagingData emission): pagingMap is applied lazily
                    // as pages load, so reading .value here means a later APPEND
                    // page — or a prefs/account change between loads — reflects
                    // the current state without rebuilding the Pager. PagingData
                    // requires non-null T, so map each entity to a nullable
                    // holder, drop the nulls, then unwrap.
                    pagingData
                        .pagingMap { entity ->
                            val viewerDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                            val prefs = moderationPreferences.prefs.value
                            PostUiOrNull(entity.toPostUi(viewerDid, prefs))
                        }.pagingFilter { it.value != null }
                        .pagingMap { requireNotNull(it.value) }
                }

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
                feedPostDao.clearAccount(accountDid)
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
