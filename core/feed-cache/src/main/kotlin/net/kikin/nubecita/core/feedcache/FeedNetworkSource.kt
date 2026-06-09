package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetListFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject

/** A fetched feed page: the wire posts plus the cursor for the next page. */
typealias FeedNetworkPage = Pair<List<FeedViewPost>, String?>

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
         */
        suspend fun fetchPage(
            feedKey: FeedKey,
            cursor: String?,
            limit: Int = DEFAULT_LIMIT,
        ): Result<FeedNetworkPage> =
            when (feedKey.feedType) {
                FeedType.FOLLOWING ->
                    fetch("getTimeline", cursor) { client ->
                        FeedService(client)
                            .getTimeline(GetTimelineRequest(cursor = cursor, limit = limit.toLong()))
                            .let { it.feed to it.cursor }
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
                            ).let { it.feed to it.cursor }
                    }

                FeedType.LIST ->
                    fetch("getListFeed", cursor) { client ->
                        FeedService(client)
                            .getListFeed(
                                GetListFeedRequest(
                                    list = AtUri(feedKey.feedUri),
                                    cursor = cursor,
                                    limit = limit.toLong(),
                                ),
                            ).let { it.feed to it.cursor }
                    }
            }

        private suspend fun fetch(
            operation: String,
            cursor: String?,
            block: suspend (client: XrpcClient) -> FeedNetworkPage,
        ): Result<FeedNetworkPage> =
            withContext(dispatcher) {
                runCatching {
                    block(xrpcClientProvider.authenticated())
                }.onFailure { throwable ->
                    // Same diagnostic shape as DefaultFeedRepository: log the
                    // throwable's concrete class so cold-start failures are
                    // attributable. javaClass.name (not qualifiedName) so
                    // anonymous/local classes still produce a non-null id.
                    Timber.tag(TAG).e(
                        throwable,
                        "%s(cursor=%s) failed: %s",
                        operation,
                        cursor,
                        throwable.javaClass.name,
                    )
                }
            }

        private companion object {
            const val TAG = "FeedNetworkSource"
            const val DEFAULT_LIMIT = 50
        }
    }
