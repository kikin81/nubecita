package net.kikin.nubecita.core.feedcache

import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
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
         * The window is read back from the stored rows, not tracked in memory,
         * so a fill interrupted by process death resumes **below** the pages
         * that already landed. Without that, a resumed fill would restart at
         * the top of the window and — because the write is an upsert keyed on
         * `position` — overwrite the batch above it rather than failing.
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

            var cursor = key.fillCursor
            val resuming = cursor != null
            var pendingBoundary = key.pendingBoundary
            val lowestPrepended =
                feedPostDao.maxNegativePosition(feedKey.accountDid, feedKey.feedType.name, feedKey.feedUri)
            var nextFree =
                if (resuming && lowestPrepended != null) {
                    lowestPrepended + 1
                } else {
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
                val first =
                    feedPostDao.firstPositionAtOrAbove(
                        feedKey.accountDid,
                        feedKey.feedType.name,
                        feedKey.feedUri,
                        nextFree,
                    )
                val room = if (first == null) CATCH_UP_ENTRY_BUDGET else first - nextFree
                if (room <= 0) return Result.success(CatchUpOutcome.CAUGHT_UP)

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
                    )
                }

                nextFree = base + entries.size
                if (projected > 0 || exhausted || rounds >= MAX_FILL_ROUNDS) {
                    return Result.success(if (exhausted) CatchUpOutcome.CAUGHT_UP else CatchUpOutcome.MORE_PENDING)
                }
                cursor = fetched.nextCursor
            }
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
