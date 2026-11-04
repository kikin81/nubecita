package net.kikin.nubecita.core.feedcache

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.RemoteMediator
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.data.models.AuthorUi
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
    ): Flow<PagingData<CachedFeedRow>>

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
     * The reader's saved place in [feedKey], or `null` when there is nothing
     * to restore.
     *
     * Resolved from the stored item key first, because that is what survives a
     * re-projection moving the row. When the key is gone — the item was
     * deleted, moderated out, or evicted — it resolves **downward** to the
     * nearest surviving row below it (D11): the items just under where the
     * reader stopped are the ones they had not read yet, so landing there
     * loses nothing, while landing above would re-show what they finished.
     *
     * Carries the resolved row's own key, not just a position, because the two
     * consumers need different things: the `Pager` opens at the position, and
     * the list scrolls to the key, which is the only identity it has for a row.
     *
     * Always `null` on a feed that cannot prepend what arrived since it was
     * last read (D14) — see [saveAnchor], which declines to store one there.
     */
    suspend fun anchor(feedKey: FeedKey): FeedAnchor?

    /**
     * The reader's saved place, as the key of the item at the top of the
     * viewport and its pixel offset within that row.
     *
     * Takes a key, not a position: the caller is the screen, which knows what
     * is on screen but not where those rows sit in the partition. Resolving
     * the key to a position is this layer's job, and storing both is what lets
     * a later restore fall back when the key is gone.
     *
     * A no-op on a feed that does not support `since` (D14): a generator
     * refreshes on open, so the partition the place referred to is gone before
     * anyone could be put back in it.
     */
    suspend fun saveAnchor(
        feedKey: FeedKey,
        itemKey: String,
        offset: Int,
    )

    /**
     * The authors of entries sitting above the reader's place that they have
     * not acknowledged — what the new-posts pill shows, and the signal for
     * whether to show it at all (empty means no pill).
     *
     * Derived from the partition rather than raised by a prepend result
     * (D20). It has to be: the writes that matter are the background worker's,
     * made while the app is away, where there is no in-session event for the
     * UI to have observed. A `Flow`, so returning to a feed the worker touched
     * raises the pill without anything asking.
     *
     * Capped at [PILL_FACES]: the pill shows a few faces, not a count.
     */
    fun newPostAuthors(feedKey: FeedKey): Flow<ImmutableList<AuthorUi>>

    /**
     * Acknowledge everything currently above the reader — what tapping the
     * pill, or scrolling up past the new entries, means.
     *
     * Recorded as a position rather than a flag so a later arrival raises the
     * pill again: anything written above the mark is new, anything below it
     * has been seen.
     */
    suspend fun dismissNewPosts(feedKey: FeedKey)

    /**
     * Drop the reader's place — what pull-to-refresh does (D8).
     *
     * The gesture means "give me a clean timeline", so restoring into the old
     * one would be the opposite of what was asked, and the rows the anchor
     * named are gone with the partition anyway.
     */
    suspend fun clearAnchor(feedKey: FeedKey)

    /**
     * Catch [feedKey]'s partition up on what arrived above its head, WITHOUT a
     * `Pager` and without clearing anything.
     *
     * The background worker's write path (D10). The worker runs on a period
     * while the app is backgrounded — exactly the window in which a reader's
     * saved position matters, and the one window the foreground guard does not
     * cover — so a clear-and-replace there would delete the scrollback and the
     * row the anchor points at between sittings.
     *
     * [CatchUpOutcome.NOT_POSSIBLE] is decided from local state, before any
     * fetch, so a caller can fall back to [refresh] without having paid for a
     * request. The worker must fall back: a partition it can neither catch up
     * on nor refresh would leave the widget frozen on whatever it last showed.
     */
    suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome>

    /**
     * Evict [feedKey]'s partition down to the newest [cap] rows, across every
     * grain the cache stores.
     *
     * NOT called from the mediator's `load()` (D-A5) — invoked off the scroll
     * path by the refresh worker. Leaves the partition's cursor row intact.
     *
     * [anchorItemPosition] is the reader's saved place, as a projection
     * position. Everything from it upward survives even when that is more than
     * [cap] rows (D19): an anchor is a reader's unread backlog, the cap is a
     * storage guard, and a guard that deletes the thing it protects is worse
     * than no guard. A catch-up can write up to half the cap above the anchor,
     * so without this the next worker run would delete the anchor and the whole
     * backlog while the app was backgrounded — the feature eating itself at
     * exactly the volume it was built for.
     *
     * [ceiling] bounds that exception, so a partition cannot grow without limit
     * just because an anchor sits deep in it.
     *
     * Resolving the stored anchor (a URI plus an offset, D6) to a position
     * belongs to the anchor work; until then callers pass `null` and get the
     * plain row-count trim.
     */
    suspend fun trimToCap(
        feedKey: FeedKey,
        cap: Int = DEFAULT_CAP,
        anchorItemPosition: Int? = null,
        ceiling: Int = HARD_CEILING,
    )

    /**
     * Purge all cached feed partitions (posts + cursors) for [accountDid].
     * Driven by the session observer on sign-out / account removal.
     */
    suspend fun clearAccount(accountDid: String)

    companion object {
        /** Per-partition retention cap (D-A5: ~500 newest posts). */
        const val DEFAULT_CAP = 500

        /**
         * The most rows a partition may hold even when an anchor asks for more.
         *
         * The anchor exception has to be bounded by something, or a reader who
         * never returns to a deep position keeps that partition alive forever.
         * Three times the cap is a worst case, not a target: reaching it needs
         * an anchor that far down, which only an unusually long unread backlog
         * produces.
         */
        const val HARD_CEILING = DEFAULT_CAP * 3

        /** Paging page size for the app feed. */
        const val PAGE_SIZE = 25

        /**
         * How many faces the new-posts pill shows.
         *
         * Three overlapping avatars and no count: the number of new posts is
         * not something a reader acts on, and a count invites the "inbox at
         * zero" feeling this feature is meant to avoid.
         */
        const val PILL_FACES = 3
    }
}

/**
 * Default [FeedRepository]. The read path maps `CachedFeedEntry → PostUi?` at map
 * time (so a later prefs/account change reflects on the next load), dropping the
 * nulls that moderation / malformed-blob produce. Reads `viewerDid` / `prefs`
 * via `.value` per item so the projection always reflects current state.
 */
@OptIn(ExperimentalPagingApi::class, ExperimentalCoroutinesApi::class)
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
        private val feedCatchUp: FeedCatchUp,
    ) : FeedRepository {
        /**
         * The [RemoteMediator] for [feedKey]. Extracted as an `open` seam so a
         * JVM unit test can swap a no-op mediator (the assisted factory needs the
         * Hilt-generated impl, which a pure-JVM test can't construct).
         */
        protected open fun mediatorFor(feedKey: FeedKey): RemoteMediator<Int, CachedFeedRow> = mediatorFactory.create(feedKey)

        override fun pagedFeed(
            feedKey: FeedKey,
            initialPosition: Int?,
        ): Flow<PagingData<CachedFeedRow>> =
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
        ): PagingSource<Int, CachedFeedRow> =
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

        override suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> = feedCatchUp.catchUp(feedKey)

        override suspend fun anchor(feedKey: FeedKey): FeedAnchor? {
            if (!feedKey.feedType.supportsSince) return null
            val state =
                partitionStateDao.state(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    ?: return null
            val key = state.anchorItemKey ?: return null
            feedItemDao
                .positionOf(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, key)
                ?.let { position ->
                    return FeedAnchor(position = position, itemKey = key, offset = state.anchorOffset ?: 0)
                }
            // The key is gone. The stored position is the only thing left to
            // resolve from, which is why it is stored alongside the key.
            val from = state.anchorPosition ?: return null
            val fallback =
                feedItemDao.firstAtOrBelow(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, from)
                    ?: return null
            // Offset 0, deliberately: the stored one measured a scroll into a
            // row that no longer exists, and applying it to a different row of
            // a different height would land somewhere arbitrary.
            return FeedAnchor(position = fallback.position, itemKey = fallback.itemKey, offset = 0)
        }

        override suspend fun saveAnchor(
            feedKey: FeedKey,
            itemKey: String,
            offset: Int,
        ) {
            // Gated here rather than at each screen: keeping a place is only
            // meaningful on a feed that can prepend what arrived since (D14).
            // A generator refreshes on open, so an anchor written for one would
            // point into a partition that no longer exists by the time anyone
            // read it — and every caller would have to remember that.
            if (!feedKey.feedType.supportsSince) return
            // No row for this key means the list moved under the write — a
            // refresh landed between the scroll settling and this call.
            // Storing a position the key does not have would make the
            // dead-anchor fallback resolve from a stale place, so skip it: the
            // next settle writes a current one.
            val position =
                feedItemDao.positionOf(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, itemKey)
                    ?: return
            partitionStateDao.saveAnchor(
                accountDid = feedKey.accountDid,
                feedType = feedKey.feedType.name,
                feedUri = feedKey.feedUri,
                itemKey = itemKey,
                position = position,
                offset = offset,
            )
        }

        override fun newPostAuthors(feedKey: FeedKey): Flow<ImmutableList<AuthorUi>> =
            partitionStateDao
                .observe(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                .flatMapLatest { state ->
                    // Dismissal wins over the anchor: once acknowledged, the
                    // mark is the new waterline and only writes above IT count.
                    // With neither, position 0 is the top of the last
                    // destructive refresh, so a prepend's negative positions
                    // are "above" and a reader at the top still sees the pill.
                    val above = state?.pillDismissedPosition ?: state?.anchorPosition ?: 0
                    feedItemDao.leafPostsAbove(
                        feedKey.accountDid,
                        feedKey.feedType.name,
                        feedKey.feedUri,
                        position = above,
                        limit = FeedRepository.PILL_FACES,
                    )
                }.map { posts ->
                    // Distinct by author: three faces from three people says
                    // more than the same person three times.
                    posts
                        .mapNotNull { post -> post.toPostViewOrNull()?.toPostUiCore(currentViewerDid())?.author }
                        .distinctBy { author -> author.did }
                        .toImmutableList()
                }

        private fun currentViewerDid(): String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        override suspend fun dismissNewPosts(feedKey: FeedKey) {
            // The current top IS the waterline: everything above the reader
            // right now is what they are acknowledging.
            val top =
                feedItemDao.minPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    ?: return
            partitionStateDao.dismissPillAbove(
                accountDid = feedKey.accountDid,
                feedType = feedKey.feedType.name,
                feedUri = feedKey.feedUri,
                position = top,
            )
        }

        override suspend fun clearAnchor(feedKey: FeedKey) = partitionStateDao.clearAnchor(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)

        override suspend fun trimToCap(
            feedKey: FeedKey,
            cap: Int,
            anchorItemPosition: Int?,
            ceiling: Int,
        ) {
            val did = feedKey.accountDid
            val type = feedKey.feedType.name
            val uri = feedKey.feedUri
            transactionRunner.run {
                // The projection first, and by row count: it is the grain the
                // reader actually sees, and the grain the anchor is expressed
                // in. The old trim bounded `feed_post` alone, which left this
                // table, its members and the post store growing without limit —
                // measured at 5510 projection rows against a 5250-row cap.
                val itemCut =
                    cutOff(
                        cap = cap,
                        ceiling = ceiling,
                        anchorPosition = anchorItemPosition,
                        positionAtOffset = { offset -> feedItemDao.positionAtOffsetFromTop(did, type, uri, offset) },
                    )
                if (itemCut != null) feedItemDao.deleteOlderThan(did, type, uri, itemCut)

                // Entries are tied to the projection by CONTENT, not by count:
                // a self-thread chain folds several entries into one item, so
                // trimming both to the same number would leave a surviving item
                // whose entries are gone and a re-projection unable to rebuild
                // it. Whatever the surviving items reference stays, and the cap
                // only ever extends that.
                val referenced = feedPostDao.oldestReferencedByProjection(did, type, uri)
                val entryCut =
                    cutOff(
                        cap = cap,
                        ceiling = ceiling,
                        anchorPosition = referenced,
                        positionAtOffset = { offset -> feedPostDao.positionAtOffsetFromTop(did, type, uri, offset) },
                    )
                if (entryCut != null) feedPostDao.deleteOlderThan(did, type, uri, entryCut)

                // Last, and reference-counted: a post with no position in any
                // partition can still be a cluster's root or parent, so
                // position arithmetic cannot see whether it is still needed
                // (D24). Running it after both deletes means the rows that just
                // went are no longer holding anything alive.
                cachedPostDao.deleteUnreferenced(did)
            }
        }

        /**
         * The oldest position to keep: the newest [cap] rows, extended to
         * [anchorPosition] when that is deeper, and clamped to [ceiling].
         *
         * `null` means nothing to delete — the grain holds fewer than [cap]
         * rows, so the cut-off is past its end.
         */
        private suspend inline fun cutOff(
            cap: Int,
            ceiling: Int,
            anchorPosition: Int?,
            positionAtOffset: (Int) -> Int?,
        ): Int? {
            val capCut = positionAtOffset(cap - 1) ?: return null
            val extended = if (anchorPosition != null && anchorPosition > capCut) anchorPosition else capCut
            val ceilingCut = positionAtOffset(ceiling - 1)
            return if (ceilingCut != null && extended > ceilingCut) ceilingCut else extended
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
 * Where a reader left a feed: the row, and how far into it.
 *
 * [position] opens the `Pager` at the right page; [itemKey] is what the list
 * scrolls to, since a key is the only identity a rendered row has; [offset]
 * puts the row back where it sat in the viewport rather than snapping it to
 * the top.
 */
data class FeedAnchor(
    val position: Int,
    val itemKey: String,
    val offset: Int,
)

/**
 * Tiny non-null carrier so a nullable [PostUi] can flow through `PagingData`
 * (whose element type is non-null) before the null-dropping `filter`.
 */
private data class PostUiOrNull(
    val value: PostUi?,
)
