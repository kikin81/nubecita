package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
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
                }
            }
    }
