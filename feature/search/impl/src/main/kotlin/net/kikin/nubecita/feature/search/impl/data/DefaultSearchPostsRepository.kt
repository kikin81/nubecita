package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.SearchPostsRequest
import io.github.kikin81.atproto.app.bsky.feed.SearchPostsV2Request
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.auth.viewerDidOrNull
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.applyModeration
import net.kikin.nubecita.core.feedmapping.toPostUiCore
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.data.models.FeedItemUi
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
        private val moderationPreferences: ModerationPreferencesRepository,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : SearchPostsRepository {
        override suspend fun searchPosts(
            query: String,
            cursor: String?,
            limit: Int,
            sort: SearchPostsSort,
        ): Result<SearchPostsPage> {
            // Fail fast on misuse rather than forwarding to the server and
            // surfacing an opaque 400. The atproto lexicon for
            // `app.bsky.feed.searchPosts` allows 1..100; SEARCH_POSTS_PAGE_LIMIT
            // is the default but production callers (the search VM in vrba.6)
            // may eventually want to vary it.
            require(limit in 1..100) {
                "limit must be in 1..100 (atproto lexicon range), got $limit"
            }
            return withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).searchPosts(
                            SearchPostsRequest(
                                q = query,
                                cursor = cursor,
                                limit = limit.toLong(),
                                sort = sort.name.lowercase(),
                            ),
                        )
                    Result.success(SearchPostsPage(items = response.posts.toModeratedItems(), nextCursor = response.cursor))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    // Never log the query itself (user-entered content) — it now
                    // reaches Crashlytics breadcrumbs. Keep cursor/sort + error identity.
                    Timber.tag(TAG).w(
                        t,
                        "searchPosts failed (cursor=%s, sort=%s): %s",
                        cursor,
                        sort,
                        t.javaClass.name,
                    )
                    Result.failure(t)
                }
            }
        }

        override suspend fun searchHashtag(
            tag: String,
            author: String?,
            cursor: String?,
            limit: Int,
            sort: SearchPostsSort,
        ): Result<SearchPostsPage> {
            require(limit in 1..100) {
                "limit must be in 1..100 (atproto lexicon range), got $limit"
            }
            return withContext(dispatcher) {
                try {
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        FeedService(client).searchPostsV2(
                            SearchPostsV2Request(
                                hashtags = listOf(tag),
                                authors = author?.let { listOf(AtIdentifier(it)) },
                                cursor = cursor,
                                limit = limit.toLong(),
                                sort = sort.v2WireValue,
                            ),
                        )
                    Result.success(SearchPostsPage(items = response.posts.toModeratedItems(), nextCursor = response.cursor))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    // The tag is public, but keep the log shape of searchPosts:
                    // no user-entered text, just paging + error identity.
                    Timber.tag(TAG).w(
                        t,
                        "searchHashtag failed (cursor=%s, sort=%s, author=%s): %s",
                        cursor,
                        sort,
                        author != null,
                        t.javaClass.name,
                    )
                    Result.failure(t)
                }
            }
        }

        /**
         * Drop hard-filtered (NSFW) results and cover warned media, off the
         * render path, against the cached prefs + viewer DID.
         */
        private fun List<PostView>.toModeratedItems(): ImmutableList<FeedItemUi.Single> {
            val prefs = moderationPreferences.prefs.value
            val viewerDid = sessionStateProvider.viewerDidOrNull
            return mapNotNull { post ->
                post
                    .toPostUiCore(viewerDid)
                    ?.applyModeration(post.labels, viewerDid, prefs, dropFiltered = true)
                    ?.let(FeedItemUi::Single)
            }.toImmutableList()
        }

        private companion object {
            const val TAG = "SearchPostsRepo"
        }
    }

/** `searchPostsV2` names the recency sort `recent` (v1 used `latest`). */
private val SearchPostsSort.v2WireValue: String
    get() =
        when (this) {
            SearchPostsSort.TOP -> "top"
            SearchPostsSort.LATEST -> "recent"
        }
