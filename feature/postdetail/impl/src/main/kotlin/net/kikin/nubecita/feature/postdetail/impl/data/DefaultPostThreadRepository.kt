package net.kikin.nubecita.feature.postdetail.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetPostThreadRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject

internal class DefaultPostThreadRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostThreadRepository {
        override suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getPostThread(
                            GetPostThreadRequest(uri = AtUri(uri)),
                        )
                    response.thread.toThreadItems()
                }.onFailure { throwable ->
                    // Mirrors the diagnostic logging convention in
                    // :feature:feed:impl/data/DefaultFeedRepository — emits the
                    // throwable's javaClass.name so the Unknown-error VM branch
                    // doesn't swallow which atproto-stack exception we hit.
                    Timber.tag(TAG).e(
                        throwable,
                        "getPostThread(uri=%s) failed: %s",
                        uri,
                        throwable.javaClass.name,
                    )
                }
            }

        private companion object {
            const val TAG = "PostThreadRepository"
        }
    }
