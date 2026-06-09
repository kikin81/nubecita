package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.RemoteMediator
import kotlinx.coroutines.flow.Flow
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
                    // Read current viewer + prefs per item so a logout / prefs
                    // toggle reflects on the next Paging load without rebuilding
                    // the Pager. PagingData requires non-null T, so map each
                    // entity to a nullable holder, drop the nulls, then unwrap.
                    val viewerDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    val prefs = moderationPreferences.prefs.value
                    pagingData
                        .pagingMap { entity -> PostUiOrNull(entity.toPostUi(viewerDid, prefs)) }
                        .pagingFilter { it.value != null }
                        .pagingMap { requireNotNull(it.value) }
                }

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
