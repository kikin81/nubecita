package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList

/**
 * `app.bsky.unspecced.getPopularFeedGenerators` fetch surface scoped to
 * `:feature:search:impl`. Stateless — the caller (Search Feeds tab VM
 * in `nubecita-vrba.11`) owns the cursor and re-issues calls for the
 * next page. Mirrors [SearchPostsRepository] / [SearchActorsRepository]
 * shape.
 *
 * **Why this lives in nubecita rather than `:core:posting` (or as a
 * generated service in atproto-kotlin):** the underlying RPC is
 * `app.bsky.unspecced.*`, and Bluesky reserves the `unspecced`
 * namespace for endpoints without stable contracts — there's no
 * DNS-published lexicon DID for it, so `npx lex install` (which
 * kikinlex's generator pipeline depends on) can't resolve it. The
 * production call falls back to the raw `XrpcClient.query(nsid, ...)`
 * with hand-written `@Serializable` request/response types in
 * `:feature:search:impl/data/`. Tracking on the kikinlex side:
 * `github.com/kikin81/atproto-kotlin/issues/108`.
 *
 * Results are projected to [net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi]
 * via a local `GeneratorView.toFeedGeneratorUi()` extension in
 * [DefaultSearchFeedsRepository].
 */
internal interface SearchFeedsRepository {
    suspend fun searchFeeds(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_FEEDS_PAGE_LIMIT,
    ): Result<SearchFeedsPage>
}

internal data class SearchFeedsPage(
    val items: ImmutableList<FeedGeneratorUi>,
    val nextCursor: String?,
)

/**
 * Default page size for `getPopularFeedGenerators`. Lexicon allows
 * 1–100; 25 matches the SearchPosts / SearchActors page size for a
 * consistent feel across the three Search tabs.
 */
internal const val SEARCH_FEEDS_PAGE_LIMIT: Int = 25
