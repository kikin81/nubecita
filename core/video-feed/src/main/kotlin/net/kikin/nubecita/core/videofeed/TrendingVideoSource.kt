package net.kikin.nubecita.core.videofeed

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber
import javax.inject.Inject

/** Keep only posts that carry a playable video embed. */
internal fun List<PostUi>.videoPostsOnly(): List<PostUi> = filter { it.embed is EmbedUi.Video }

/**
 * Project a `getFeed` page to the vertical feed's items: map each wire post to
 * [PostUi] (dropping any that don't project) and keep only those with a video
 * embed. `thevids` can surface mixed content, so the filter guarantees every
 * item is playable. Pure — unit-tested without a network.
 */
fun toVideoPosts(feed: List<FeedViewPost>): List<PostUi> = feed.mapNotNull { it.post.toPostUiCore() }.videoPostsOnly()

/**
 * [VideoFeedSource] over Bluesky's official "Video" custom feed
 * ([THEVIDS_FEED_URI]), fetched via `app.bsky.feed.getFeed` with cursor
 * pagination. The MVP trending source for the vertical video feed epic.
 */
internal class DefaultTrendingVideoSource
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : VideoFeedSource {
        override suspend fun loadPage(cursor: String?): Result<VideoFeedPage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getFeed(
                            GetFeedRequest(
                                feed = AtUri(THEVIDS_FEED_URI),
                                cursor = cursor,
                                limit = PAGE_LIMIT,
                            ),
                        )
                    VideoFeedPage(items = toVideoPosts(response.feed), cursor = response.cursor)
                }.onFailure { throwable ->
                    // runCatching also catches CancellationException; rethrow it so structured
                    // coroutine cancellation propagates instead of being swallowed into a Result.
                    if (throwable is CancellationException) throw throwable
                    // Log only the error identity — the feed URI is public, but keep parity
                    // with the redaction discipline used across the XRPC repositories.
                    Timber.tag(TAG).w(throwable, "trending getFeed failed: %s", throwable.javaClass.name)
                }
            }

        private companion object {
            const val TAG = "TrendingVideoSource"

            /** Bluesky's official "Video" custom feed (rkey `thevids`, same DID as Discover). */
            const val THEVIDS_FEED_URI = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.generator/thevids"
            const val PAGE_LIMIT = 30L
        }
    }
