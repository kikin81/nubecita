package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject

internal class DefaultFeedRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : FeedRepository {
        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).getTimeline(
                            GetTimelineRequest(
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    TimelinePage(
                        posts = response.feed.mapNotNull { it.toPostUiOrNull() }.toImmutableList(),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    // Surfaces the throwable identity that FeedViewModel's
                    // toFeedError() collapses into FeedError.Unknown — without
                    // this, the cold-start "Something went wrong" screen tells
                    // us nothing about which exception the atproto stack
                    // produced. See nubecita-09o.
                    Timber.tag(TAG).e(
                        throwable,
                        "getTimeline(cursor=%s) failed: %s",
                        cursor,
                        throwable::class.qualifiedName,
                    )
                }
            }

        private companion object {
            const val TAG = "FeedRepository"
        }
    }
