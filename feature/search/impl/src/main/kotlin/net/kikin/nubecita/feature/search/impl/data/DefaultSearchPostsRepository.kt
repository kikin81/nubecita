package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.SearchPostsRequest
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toFlatFeedItemUiSingle
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SearchPostsRepository] backed by the atproto-kotlin SDK's
 * [FeedService]. Routes through the authenticated [XrpcClientProvider]
 * (same client every other reader uses).
 *
 * Sends `q` + `cursor` + `limit` and projects [FeedService.searchPosts]
 * response via [toFlatFeedItemUiSingle] (flat — search results never
 * render reply clusters). Posts that fail to project (malformed
 * embedded record) are filtered out at the collection boundary.
 *
 * Cancellation re-throws *before* the Timber log so the VM's
 * `mapLatest` operator in `nubecita-vrba.6` can cancel the upstream
 * coroutine cleanly when the user keeps typing.
 */
@Singleton
internal class DefaultSearchPostsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchPostsRepository {
        override suspend fun searchPosts(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchPostsPage> =
            withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).searchPosts(
                            SearchPostsRequest(
                                q = query,
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    Result.success(
                        SearchPostsPage(
                            items =
                                response.posts
                                    .mapNotNull { it.toFlatFeedItemUiSingle() }
                                    .toImmutableList(),
                            nextCursor = response.cursor,
                        ),
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).e(t, "searchPosts(q=%s, cursor=%s) failed: %s", query, cursor, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            private const val TAG = "SearchPostsRepo"
        }
    }
