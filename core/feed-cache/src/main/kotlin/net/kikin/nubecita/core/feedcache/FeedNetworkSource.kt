package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetListFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject

/**
 * A fetched feed page.
 *
 * A named type rather than a `Pair` because it carries three values, two of
 * them nullable cursors pointing in opposite directions. Named access is the
 * point: `page.startCursor` says which end of the feed it describes where a
 * third tuple slot would not. (Destructuring still works positionally, so this
 * is readability rather than a guarantee.)
 */
data class FeedNetworkPage(
    val posts: List<FeedViewPost>,
    /** Cursor for the next page *older* than this one, or null at the end. */
    val nextCursor: String?,
    /**
     * Cursor identifying the **newest** item in this page, to pass as `since`
     * on a later catch-up fetch. Null for a method that does not provide one —
     * `app.bsky.feed.getFeed` has no `since`, so a generator feed cannot bound
     * a catch-up range at all.
     */
    val startCursor: String? = null,
)

/**
 * Cursor-based feed fetch for `:core:feed-cache`, ported from
 * `:feature:feed:impl`'s `DefaultFeedRepository`. Unlike that repository this
 * source does NOT map to `PostUi` — the RemoteMediator (a later PR) writes the
 * wire posts straight to the cache via [FeedViewPost.toFeedPostEntity], and the
 * read path maps to `PostUi`. So this returns the raw `(wire posts, nextCursor)`
 * pair.
 *
 * One entry per [FeedType]:
 * - [FeedType.FOLLOWING] → `getTimeline`
 * - [FeedType.DISCOVER] / [FeedType.CUSTOM] → `getFeed(feedUri)`
 * - [FeedType.LIST] → `getListFeed(feedUri)`
 *
 * All calls run on [IoDispatcher] against an authenticated client from
 * [XrpcClientProvider] (the refresh-mutex path), and surface failures as
 * `Result.failure` (network error, 5xx, `NoSessionException`).
 */
class FeedNetworkSource
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        /**
         * Fetch one page of [feedKey]'s feed starting at [cursor] (null for the
         * first page).
         *
         * [since] bounds the fetch to items *newer* than that position, for the
         * catch-up a prepend performs. Only `getTimeline` and `getListFeed`
         * accept it; passing it for a generator feed is a caller error, and it
         * is logged and dropped rather than silently ignored — a dropped `since`
         * turns a bounded catch-up into an unbounded walk backwards through the
         * whole feed.
         */
        suspend fun fetchPage(
            feedKey: FeedKey,
            cursor: String?,
            since: String? = null,
            limit: Int = DEFAULT_LIMIT,
        ): Result<FeedNetworkPage> {
            val effectiveSince =
                if (since != null && !feedKey.feedType.supportsSince) {
                    Timber.tag(TAG).w(
                        "since passed for %s, which has no `since` parameter — dropping it",
                        feedKey.feedType,
                    )
                    null
                } else {
                    since
                }
            return when (feedKey.feedType) {
                FeedType.FOLLOWING ->
                    fetch("getTimeline", cursor) { client ->
                        FeedService(client)
                            .getTimeline(
                                GetTimelineRequest(
                                    cursor = cursor,
                                    since = effectiveSince,
                                    limit = limit.toLong(),
                                ),
                            ).let { FeedNetworkPage(it.feed, it.cursor, it.startCursor) }
                    }

                FeedType.DISCOVER, FeedType.CUSTOM ->
                    fetch("getFeed", cursor) { client ->
                        FeedService(client)
                            .getFeed(
                                GetFeedRequest(
                                    feed = AtUri(feedKey.feedUri),
                                    cursor = cursor,
                                    limit = limit.toLong(),
                                ),
                                // No `since`: getFeed does not accept one.
                            ).let { FeedNetworkPage(it.feed, it.cursor) }
                    }

                FeedType.LIST ->
                    fetch("getListFeed", cursor) { client ->
                        FeedService(client)
                            .getListFeed(
                                GetListFeedRequest(
                                    list = AtUri(feedKey.feedUri),
                                    cursor = cursor,
                                    since = effectiveSince,
                                    limit = limit.toLong(),
                                ),
                            ).let { FeedNetworkPage(it.feed, it.cursor, it.startCursor) }
                    }
            }
        }

        private suspend fun fetch(
            operation: String,
            cursor: String?,
            block: suspend (client: XrpcClient) -> FeedNetworkPage,
        ): Result<FeedNetworkPage> =
            withContext(dispatcher) {
                try {
                    Result.success(block(xrpcClientProvider.authenticated()))
                } catch (cancellation: CancellationException) {
                    // Cooperative cancellation (feed refresh / navigating away
                    // cancels the in-flight load) must propagate — not be logged
                    // as an error or collapsed into Result.failure. runCatching
                    // would swallow it, so catch + rethrow explicitly.
                    throw cancellation
                } catch (throwable: Throwable) {
                    // Same diagnostic shape as DefaultFeedRepository: log the
                    // throwable's concrete class so cold-start failures are
                    // attributable. javaClass.name (not qualifiedName) so
                    // anonymous/local classes still produce a non-null id.
                    Timber.tag(TAG).w(
                        throwable,
                        "%s(cursor=%s) failed: %s",
                        operation,
                        cursor,
                        throwable.javaClass.name,
                    )
                    Result.failure(throwable)
                }
            }

        private companion object {
            const val TAG = "FeedNetworkSource"
            const val DEFAULT_LIMIT = 50
        }
    }
