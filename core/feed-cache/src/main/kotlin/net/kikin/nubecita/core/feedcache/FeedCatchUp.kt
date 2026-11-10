package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import javax.inject.Inject

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
         */
        suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> {
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
                // A full window is the budget cap from D5 — the seam offers
                // jump-to-newest rather than a load-more nobody taps twice.
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
                val room = if (first == null) CATCH_UP_ENTRY_BUDGET else first - nextFree
                if (room <= 0) {
                    // The window is full: this is as far as a catch-up may go
                    // (D5). The seam stays, marked exhausted, so the reader is
                    // offered a jump to the newest rather than a fill that
                    // cannot complete.
                    transactionRunner.run { writeSeam(feedKey, position = nextFree, exhausted = true) }
                    return Result.success(CatchUpOutcome.CAUGHT_UP)
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
                        writeSeam(feedKey, position = base + projected, exhausted = false)
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
         * Moves the partition's seam marker to [position].
         *
         * Cleared first, because at most one seam exists and the boundary
         * moves down as each fill page lands — leaving the old one would show
         * the reader two breaks in a timeline that has one.
         */
        private suspend fun writeSeam(
            feedKey: FeedKey,
            position: Int,
            exhausted: Boolean,
        ) {
            feedItemDao.clearSeam(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
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
