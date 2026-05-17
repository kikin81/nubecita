package net.kikin.nubecita.feature.videoplayer.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostsRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toFlatFeedItemUiSingle
import net.kikin.nubecita.data.models.EmbedUi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [VideoPostResolver] backed by `app.bsky.feed.getPosts`.
 *
 * Routes through [XrpcClientProvider]'s authenticated client. The
 * lexicon's `getPosts` accepts up to 25 URIs per call; we ask for
 * exactly one. The response is projected via the same
 * `toFlatFeedItemUiSingle` mapper used by the feed + search, so the
 * embed extraction matches the rest of the app's contract.
 *
 * Failure modes (each surfaces as `Result.failure`):
 *  - Network / timeout / HTTP 4xx-5xx from atproto.
 *  - Response has zero posts (post deleted or invalid URI).
 *  - Post exists but has no video embed (caller used the wrong URI for
 *    a non-video post). Wrapped as an [IllegalStateException].
 */
@Singleton
internal class DefaultVideoPostResolver
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : VideoPostResolver {
        override suspend fun resolve(postUri: String): Result<ResolvedVideoPost> =
            withContext(dispatcher) {
                // Log the rkey only (segment after the final `/`) — the
                // AtUri's DID segment is third-party PII. The rkey alone
                // identifies the post within a known dataset; this matches
                // the redaction policy `DefaultPostThreadRepository` and
                // `:core:auth` use across the codebase.
                val rkey = postUri.substringAfterLast('/')
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getPosts(
                            GetPostsRequest(uris = listOf(AtUri(postUri))),
                        )
                    val post =
                        response.posts.firstOrNull()
                            ?: return@withContext Result.failure(
                                IllegalStateException("No post returned for rkey=$rkey"),
                            )
                    val flat =
                        post.toFlatFeedItemUiSingle()
                            ?: return@withContext Result.failure(
                                IllegalStateException("Post rkey=$rkey did not map to a feed item"),
                            )
                    val video =
                        flat.post.embed as? EmbedUi.Video
                            ?: return@withContext Result.failure(
                                IllegalStateException("Post rkey=$rkey has no video embed"),
                            )
                    Result.success(
                        ResolvedVideoPost(
                            playlistUrl = video.playlistUrl,
                            posterUrl = video.posterUrl,
                            durationSeconds = video.durationSeconds,
                            altText = video.altText,
                            aspectRatio = video.aspectRatio,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "resolve(rkey=%s) failed: %s", rkey, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            const val TAG = "VideoPostResolver"
        }
    }
