package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetListFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject

class DefaultFeedRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : FeedRepository {
        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            fetchPage("getTimeline", cursor) {
                FeedService(it)
                    .getTimeline(
                        GetTimelineRequest(
                            cursor = cursor,
                            limit = limit.toLong(),
                        ),
                    ).let { response -> response.feed to response.cursor }
            }

        override suspend fun getFeed(
            feedUri: String,
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            fetchPage("getFeed", cursor) {
                FeedService(it)
                    .getFeed(
                        GetFeedRequest(
                            feed = AtUri(feedUri),
                            cursor = cursor,
                            limit = limit.toLong(),
                        ),
                    ).let { response -> response.feed to response.cursor }
            }

        override suspend fun getListFeed(
            listUri: String,
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            fetchPage("getListFeed", cursor) {
                FeedService(it)
                    .getListFeed(
                        GetListFeedRequest(
                            list = AtUri(listUri),
                            cursor = cursor,
                            limit = limit.toLong(),
                        ),
                    ).let { response -> response.feed to response.cursor }
            }

        /**
         * Shared fetch-and-map pipeline for all three feed kinds. [block]
         * issues the kind-specific XRPC call against an authenticated
         * client and returns the wire `(feed, cursor)` pair; this builds
         * the one canonical [TimelinePage] shape via the same
         * `toFeedItemsUi()` mapper. No kind-specific mapping branch — the
         * three kinds all decode `List<FeedViewPost>`.
         */
        private suspend fun fetchPage(
            operation: String,
            cursor: String?,
            block: suspend (client: XrpcClient) -> Pair<List<FeedViewPost>, String?>,
        ): Result<TimelinePage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val (feed, nextCursor) = block(client)
                    TimelinePage(
                        feedItems = feed.toFeedItemsUi(),
                        nextCursor = nextCursor,
                        wirePosts = feed.toImmutableList(),
                    )
                }.onFailure { throwable ->
                    // Surfaces the throwable identity that FeedViewModel's
                    // toFeedError() collapses into FeedError.Unknown — without
                    // this, the cold-start "Something went wrong" screen tells
                    // us nothing about which exception the atproto stack
                    // produced. See nubecita-09o.
                    // javaClass.name (not ::class.qualifiedName) so anonymous /
                    // local classes still produce a non-null identifier — this
                    // log exists specifically to identify the throwable, so
                    // "null" here would defeat the entire diagnostic.
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
            const val TAG = "FeedRepository"
        }
    }
