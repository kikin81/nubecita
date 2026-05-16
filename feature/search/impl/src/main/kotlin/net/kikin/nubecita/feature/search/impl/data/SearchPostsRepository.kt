package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.FeedItemUi

/**
 * `app.bsky.feed.searchPosts` fetch surface scoped to
 * `:feature:search:impl`. Stateless — the caller (Search Posts tab VM
 * in `nubecita-vrba.6`) owns the cursor and re-issues calls for the
 * next page. Mirrors [net.kikin.nubecita.feature.feed.impl.data.FeedRepository]'s
 * shape.
 *
 * Results are projected via
 * [net.kikin.nubecita.core.feedmapping.toFlatFeedItemUiSingle], which
 * ignores reply context — `searchPosts` results render as flat,
 * disconnected post cards regardless of whether a given hit is a reply.
 */
internal interface SearchPostsRepository {
    suspend fun searchPosts(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_POSTS_PAGE_LIMIT,
    ): Result<SearchPostsPage>
}

internal data class SearchPostsPage(
    val items: ImmutableList<FeedItemUi.Single>,
    val nextCursor: String?,
)

/**
 * Default page size for `searchPosts` requests. Lexicon allows 1–100;
 * 25 matches the search-data-layers spec default (slightly lower than
 * the timeline's 30 because search results are typically scanned,
 * not deeply scrolled).
 */
internal const val SEARCH_POSTS_PAGE_LIMIT: Int = 25
