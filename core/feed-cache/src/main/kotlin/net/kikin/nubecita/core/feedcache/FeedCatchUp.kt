package net.kikin.nubecita.core.feedcache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.FeedCatchUpExhausted
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import net.kikin.nubecita.core.analytics.FeedType as AnalyticsFeedType

/**
 * What one catch-up was able to do.
 *
 * [NOT_POSSIBLE] is decided from local state alone — no fetch happens — so a
 * caller can fall back to the destructive refresh without having paid for a
 * request first. That matters for the background worker: a partition it cannot
 * catch up on still has to be refreshed somehow, or the widget would freeze on
 * whatever it last showed.
 */
enum class CatchUpOutcome {
    /** The range is exhausted: the cached head now meets the live feed. */
    CAUGHT_UP,

    /** A page landed and more of the range remains, resumable from the stored fill cursor. */
    MORE_PENDING,

    /**
     * The fill reached its budget before the range was exhausted, and was
     * completed with a passive gap marker rather than left pending: the
     * boundary has moved to the newest entry it wrote, so the next catch-up
     * fetches what arrives after that. Callers that only decide whether to keep
     * paging treat it as [CAUGHT_UP]; it is separate so the cap can be counted
     * (nubecita-2xk0).
     */
    CAUGHT_UP_WITH_GAP,

    /** No bounded prepend exists here — a cold partition, no recorded boundary, or a feed whose XRPC method takes no `since`. */
    NOT_POSSIBLE,
}

/**
 * The additive catch-up for one cached feed partition, extracted as a shared
 * unit for the same reason [FeedRefresher] was: two callers must drive
 * identical logic. Paging's `PREPEND` reaches it through
 * [FeedRemoteMediator], and the background widget worker reaches it through
 * [FeedRepository.catchUp] with no `Pager` at all (D10).
 *
 * It lives here rather than on [FeedRepository] to avoid a
 * repository↔mediator cycle: `DefaultFeedRepository` constructs the mediator,
 * so the mediator must not depend on the repository. Both depend on this.
 *
 * The algorithm, and why each part of it is shaped that way, is documented on
 * [catchUp].
 */
internal class FeedCatchUp
    @Inject
    constructor(
        private val networkSource: FeedNetworkSource,
        private val feedPostDao: FeedPostDao,
        private val feedItemDao: FeedItemDao,
        private val cachedPostDao: CachedPostDao,
        private val projectionWriter: FeedProjectionWriter,
        private val remoteKeyDao: FeedRemoteKeyDao,
        private val transactionRunner: FeedCacheTransactionRunner,
        private val analytics: AnalyticsClient,
    ) {
        /**
         * Fetches the entries that arrived above the cached head and writes them
         * there, moving nothing that is already stored.
         *
         * The range is bounded by the partition's own boundary cursor rather
         * than by elapsed time (D5): `getTimeline` returns the `since` value
         * back as its cursor once the range is exhausted, so "am I done" is a
         * fact the server states rather than a guess from how long the reader
         * was gone.
         *
         * Positions march **downward** through a window reserved below the
         * partition's current minimum (D17). The newest slice takes the most
         * negative end, each later slice the block beneath it, and nothing
         * already written is renumbered — which is the whole point, since
         * renumbering would invalidate a live `PagingSource` mid-scroll.
         *
         * Where the next page goes is persisted with the fill cursor, not
         * tracked in memory, so a fill interrupted by process death resumes
         * **directly below** the pages that already landed. It cannot be read
         * back from the rows: every catch-up reserves a fresh window below the
         * previous one, so the partition holds several prepend blocks and
         * nothing marks which is the current fill's. Deriving it from the
         * highest negative position picked the oldest block and filed the
         * newest posts below yesterday's (nubecita-lrnv.1).
         *
         * One call returns as soon as it has written rendered items; the rest
         * of the range is fetched by the next call, resuming from the persisted
         * fill cursor. A barren page — every entry dropped by the reply tuners
         * or de-duplication — is followed under [MAX_FILL_ROUNDS], because a
         * call that adds nothing gives its caller no reason to ask again.
         *
         * `CancellationException` propagates: [FeedNetworkSource.fetchPage]
         * rethrows it unwrapped, and `getOrElse` only handles the failure
         * value, so a cancelled fetch escapes rather than being miscollapsed
         * into a failure.
         *
         * Calls for one partition run one at a time. Paging's PREPEND, the
         * feed's "show newest" and the background worker can all ask, and two
         * fills interleaving would each read the same stored resume point and
         * write their pages over one another.
         */
        suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> = partitionLocks.computeIfAbsent(feedKey) { Mutex() }.withLock { catchUpLocked(feedKey) }

        private suspend fun catchUpLocked(feedKey: FeedKey): Result<CatchUpOutcome> {
            if (!feedKey.feedType.supportsSince) return Result.success(CatchUpOutcome.NOT_POSSIBLE)
            val key =
                remoteKeyDao.remoteKey(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    ?: return Result.success(CatchUpOutcome.NOT_POSSIBLE)
            // No boundary means no range. An unbounded fetch here would walk
            // backwards through the whole feed, and a partition written before
            // the boundary was recorded has none — so the caller falls back to
            // the destructive refresh, which records one.
            val since = key.boundarySince ?: return Result.success(CatchUpOutcome.NOT_POSSIBLE)
            if (feedPostDao.maxPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) == null) {
                // A cold partition has no head to prepend above.
                return Result.success(CatchUpOutcome.NOT_POSSIBLE)
            }

            // Member rows a pre-reservation marker write left under a seam: the
            // marker replaced an item, the item's members stayed. Cheap and
            // idempotent, so it simply runs on every catch-up.
            transactionRunner.run {
                feedItemDao.deleteMembersUnderSeams(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            }

            // A fill cursor without a recorded position is a fill started
            // before the position was stored. Where it was writing is not
            // knowable, so it starts over from the newest page instead.
            val resumeAt = key.fillNextPosition?.takeIf { key.fillCursor != null }
            val resuming = resumeAt != null
            var cursor = if (resuming) key.fillCursor else null
            var pendingBoundary = if (resuming) key.pendingBoundary else null
            var nextFree =
                resumeAt ?: run {
                    val min = feedPostDao.minPosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri) ?: 0
                    minOf(min, 0) - CATCH_UP_ENTRY_BUDGET
                }

            var rounds = 0
            while (true) {
                rounds++
                // What is actually free below the next occupied row. The round
                // budget is per-call, so a fill resumed often enough would
                // otherwise walk past its window and silently upsert over
                // stored entries; asking the database turns that into a stop.
                // A full window is the budget cap from D5: the fill completes
                // there with a passive gap marker (completeWithGap) rather than
                // a load-more nobody taps twice, or a pending fill that stalls.
                //
                // Both tables are asked. A local re-projection packs items
                // contiguously while catch-up windows leave entries sparse, so
                // a slot free in `feed_post` can hold a projected item — and
                // the projection write is an upsert, so writing there would
                // replace a row the reader can see.
                val first =
                    listOfNotNull(
                        feedPostDao.firstPositionAtOrAbove(
                            feedKey.accountDid,
                            feedKey.feedType.name,
                            feedKey.feedUri,
                            nextFree,
                        ),
                        feedItemDao.firstItemPositionAtOrAbove(
                            feedKey.accountDid,
                            feedKey.feedType.name,
                            feedKey.feedUri,
                            nextFree,
                        ),
                    ).minOrNull()
                // The slot just above `first` is the window's reserved marker
                // slot, so entries stop one short of it. A marker is a row keyed
                // on position like any item, and written over one it would
                // replace a post the reader can see (nubecita-2xk0.1).
                val room = if (first == null) CATCH_UP_ENTRY_BUDGET else first - 1 - nextFree
                if (first != null && room <= 0) {
                    // The window is full: this is as far as one fill may go
                    // (D5). It is completed with a gap rather than left pending
                    // — a pending fill would resume into this same full window
                    // on every later catch-up and never fetch again.
                    return Result.success(completeWithGap(feedKey, since, pendingBoundary, markerAt = first - 1))
                }

                val fetched =
                    networkSource
                        .fetchPage(
                            feedKey,
                            cursor = cursor,
                            since = since,
                            limit = minOf(FeedNetworkSource.DEFAULT_LIMIT, room),
                        ).getOrElse { return Result.failure(it) }
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
                    // The boundary between what just arrived and what the
                    // reader already read. A row, not an inferred gap: a
                    // prepend leaves the unused end of its window empty, so
                    // gaps are normal and arithmetic cannot tell one from a
                    // seam (D23). Exhausted ranges have no boundary left to
                    // mark, so the marker goes away instead.
                    if (exhausted) {
                        feedItemDao.clearSeam(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
                    } else {
                        // Where the next fill page starts: a slot no entry holds
                        // yet. base + projected can be one, whenever the
                        // projection dropped rows from this page.
                        writeSeam(feedKey, position = base + entries.size, exhausted = false)
                    }
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
                        fillNextPosition = if (exhausted) null else base + entries.size,
                    )
                }

                nextFree = base + entries.size
                if (projected > 0 || exhausted || rounds >= MAX_FILL_ROUNDS) {
                    return Result.success(if (exhausted) CatchUpOutcome.CAUGHT_UP else CatchUpOutcome.MORE_PENDING)
                }
                cursor = fetched.nextCursor
            }
        }

        /**
         * Ends a fill that reached its budget before reaching the reader's
         * posts, leaving a passive gap marker at [markerAt] (nubecita-2xk0).
         *
         * The boundary moves to [pendingBoundary] — the newest page this fill
         * fetched — and the fill state is cleared, so the next catch-up starts
         * a fresh fill from the newest post and asks only for what arrived
         * after that one. Leaving the fill pending instead is what stalled
         * ingestion: every later catch-up resumed into the full window, found no
         * room, and returned without fetching.
         *
         * This is also how a partition already in that stalled state recovers:
         * its first catch-up lands here.
         *
         * A missing [pendingBoundary] should not happen for a feed that accepts
         * `since`. If it does, the fill state is still cleared and the old
         * boundary kept: the next fill walks the range again from the newest,
         * and the prepend projection keeps posts already shown from rendering
         * twice. Cleared either way, because keeping the state is the stall.
         */
        private suspend fun completeWithGap(
            feedKey: FeedKey,
            since: String,
            pendingBoundary: String?,
            markerAt: Int,
        ): CatchUpOutcome {
            if (pendingBoundary == null) {
                Timber.e("Feed catch-up capped with no pending boundary in %s; keeping the old one", feedKey.feedType)
            }
            // The window this fill used: from its top down to the marker slot,
            // which is where a window allocated below the minimum always ends.
            val written =
                feedPostDao.countInRange(
                    feedKey.accountDid,
                    feedKey.feedType.name,
                    feedKey.feedUri,
                    from = markerAt + 1 - CATCH_UP_ENTRY_BUDGET,
                    until = markerAt,
                )
            transactionRunner.run {
                writeSeam(feedKey, position = markerAt, exhausted = true)
                remoteKeyDao.updatePrependCursors(
                    accountDid = feedKey.accountDid,
                    feedType = feedKey.feedType.name,
                    feedUri = feedKey.feedUri,
                    boundarySince = pendingBoundary ?: since,
                    fillCursor = null,
                    pendingBoundary = null,
                    fillNextPosition = null,
                )
            }
            analytics.log(FeedCatchUpExhausted(feedKey.feedType.toAnalyticsFeedType(), postsFetched = written))
            return CatchUpOutcome.CAUGHT_UP_WITH_GAP
        }

        /**
         * Moves the partition's active seam to [position], or turns it into a
         * passive gap marker there when [exhausted].
         *
         * The active seam is cleared first: at most one exists, and it moves
         * down as each fill page lands. Passive gap markers from earlier fills
         * are left alone — each marks a break the reader can still reach.
         *
         * Never written over a stored entry or a projected item. The marker is
         * a row keyed on position, so writing it there would replace a post;
         * every caller picks a slot reserved for it, and a slot that is not
         * free (a window filled before the reservation existed) is skipped and
         * reported rather than overwritten.
         */
        private suspend fun writeSeam(
            feedKey: FeedKey,
            position: Int,
            exhausted: Boolean,
        ) {
            feedItemDao.clearSeam(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            val taken =
                feedPostDao.firstPositionAtOrAbove(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, position) == position ||
                    feedItemDao.firstItemPositionAtOrAbove(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri, position) == position
            if (taken) {
                Timber.e("Feed seam marker skipped: position %d in %s is occupied", position, feedKey.feedType)
                return
            }
            feedItemDao.upsert(
                listOf(
                    FeedItemEntity(
                        accountDid = feedKey.accountDid,
                        feedType = feedKey.feedType.name,
                        feedUri = feedKey.feedUri,
                        position = position,
                        rowType = FeedItemEntity.ROW_TYPE_SEAM,
                        itemKey = "${CachedFeedRow.SEAM_KEY_PREFIX}$position",
                        suppressedReplyCount = 0,
                        seamExhausted = exhausted,
                    ),
                ),
            )
        }

        internal companion object {
            /**
             * One lock per partition, shared by every instance: the catch-up is
             * constructed unscoped, once per mediator and once per repository,
             * so an instance field would serialize nothing.
             */
            private val partitionLocks = ConcurrentHashMap<FeedKey, Mutex>()

            /**
             * How many consecutive barren pages to follow in one call.
             *
             * Matches the append path's bound: an aggressively filtered account
             * could otherwise walk a large stretch of its timeline inside one
             * load.
             */
            const val MAX_FILL_ROUNDS = 3

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

/** The analytics vocabulary's name for a cached feed's kind. */
private fun FeedType.toAnalyticsFeedType(): AnalyticsFeedType =
    when (this) {
        FeedType.FOLLOWING -> AnalyticsFeedType.Following
        FeedType.DISCOVER -> AnalyticsFeedType.Discover
        FeedType.CUSTOM -> AnalyticsFeedType.Custom
        FeedType.LIST -> AnalyticsFeedType.List
    }
