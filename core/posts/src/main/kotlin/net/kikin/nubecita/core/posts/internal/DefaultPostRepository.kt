package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostsRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.posts.PostNotFoundException
import net.kikin.nubecita.core.posts.PostProjectionException
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber
import javax.inject.Inject

internal class DefaultPostRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostRepository {
        override suspend fun getPost(uri: String): Result<PostUi> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getPosts(
                            GetPostsRequest(uris = listOf(AtUri(uri))),
                        )
                    val post = response.posts.firstOrNull() ?: throw PostNotFoundException(uri)
                    post.toPostUiCore() ?: throw PostProjectionException(uri)
                }.onFailure { throwable ->
                    // Mirrors DefaultPostThreadRepository's redaction policy:
                    // log the rkey segment only (the AtUri's DID is third-party
                    // PII) and the throwable's javaClass.name so the consumer's
                    // Unknown-error branch doesn't swallow which exception type
                    // we hit.
                    Timber.tag(TAG).e(
                        throwable,
                        "getPost(rkey=%s) failed: %s",
                        uri.substringAfterLast('/'),
                        throwable.javaClass.name,
                    )
                }
            }

        private companion object {
            const val TAG = "PostRepository"
        }
    }
