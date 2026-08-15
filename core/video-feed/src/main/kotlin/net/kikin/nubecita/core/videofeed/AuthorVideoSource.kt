package net.kikin.nubecita.core.videofeed

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetAuthorFeedRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.auth.viewerDidOrNull
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import timber.log.Timber

/** The `posts_with_video` author-feed request. Pure, so the critical filter is unit-tested. */
internal fun authorVideoFeedRequest(
    actor: String,
    cursor: String?,
): GetAuthorFeedRequest =
    GetAuthorFeedRequest(
        actor = AtIdentifier(actor),
        filter = "posts_with_video",
        cursor = cursor,
        limit = AUTHOR_VIDEO_PAGE_LIMIT,
    )

/**
 * [VideoFeedSource] over a single actor's videos, via `app.bsky.feed.getAuthorFeed`
 * with `filter = "posts_with_video"` (a first-class lexicon filter). Backs the
 * profile-videos entry (epic nubecita-zdv8 Slice 6). Mirrors [DefaultTrendingVideoSource]
 * but scoped to one author; reuses the shared [toVideoPosts] mapper.
 */
internal class AuthorVideoSource
    @AssistedInject
    constructor(
        @Assisted private val actor: String,
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : VideoFeedSource {
        override suspend fun loadPage(cursor: String?): Result<VideoFeedPage> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    val client = xrpcClientProvider.authenticated()
                    val response = FeedService(client).getAuthorFeed(authorVideoFeedRequest(actor, cursor))
                    VideoFeedPage(items = toVideoPosts(response.feed, sessionStateProvider.viewerDidOrNull), cursor = response.cursor)
                }.onFailure { throwable ->
                    // runCatching also catches CancellationException; rethrow so structured
                    // coroutine cancellation propagates instead of being swallowed into a Result.
                    Timber.tag(TAG).w(throwable, "author getAuthorFeed failed: %s", throwable.javaClass.name)
                }
            }

        @AssistedFactory
        interface Factory {
            fun create(actor: String): AuthorVideoSource
        }

        private companion object {
            const val TAG = "AuthorVideoSource"
        }
    }

private const val AUTHOR_VIDEO_PAGE_LIMIT = 30L
