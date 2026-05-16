package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.ActorUi

/**
 * `app.bsky.actor.searchActors` fetch surface scoped to
 * `:feature:search:impl`. Stateless — the caller (Search People tab
 * VM in `nubecita-vrba.7`) owns the cursor and re-issues calls for
 * the next page. Mirrors [SearchPostsRepository]'s shape.
 *
 * Results are projected to [ActorUi] via a local
 * `ProfileView.toActorUi()` extension co-located in
 * [DefaultSearchActorsRepository].
 */
internal interface SearchActorsRepository {
    suspend fun searchActors(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_ACTORS_PAGE_LIMIT,
    ): Result<SearchActorsPage>
}

internal data class SearchActorsPage(
    val items: ImmutableList<ActorUi>,
    val nextCursor: String?,
)

/**
 * Default page size for `searchActors` requests. Lexicon allows 1–100;
 * 25 matches the search-data-layers spec default.
 */
internal const val SEARCH_ACTORS_PAGE_LIMIT: Int = 25
