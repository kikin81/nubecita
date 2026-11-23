package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.StarterPackUi

/**
 * `app.bsky.graph.searchStarterPacksV2` fetch surface for the Packs tab and the Summary's packs
 * section. Stateless: the caller owns the cursor and asks for the next page. Mirrors
 * [SearchFeedsRepository].
 */
internal interface SearchStarterPacksRepository {
    suspend fun searchStarterPacks(
        query: String,
        cursor: String?,
        limit: Int = SEARCH_STARTER_PACKS_PAGE_LIMIT,
    ): Result<SearchStarterPacksPage>
}

internal data class SearchStarterPacksPage(
    val items: ImmutableList<StarterPackUi>,
    val nextCursor: String?,
)

/**
 * Page size for `searchStarterPacksV2` (the lexicon allows 1–100). 25, like every other search
 * tab, so a tab and the Summary of the same search can share one first page.
 */
internal const val SEARCH_STARTER_PACKS_PAGE_LIMIT: Int = 25
