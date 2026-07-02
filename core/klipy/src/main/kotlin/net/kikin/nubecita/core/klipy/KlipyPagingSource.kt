package net.kikin.nubecita.core.klipy

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaUi

/**
 * Paging 3 source over any KLIPY page-based feed. It is deliberately decoupled
 * from [KlipyRepository]: the picker builds one per (media type × query/category)
 * by closing [fetchPage] over the right repository call, e.g.
 *
 * ```
 * Pager(PagingConfig(pageSize = 30)) {
 *     KlipyPagingSource { page -> repository.search(type, query, page) }
 * }
 * ```
 *
 * KLIPY paginates by 1-based page number and reports `has_next` (not a cursor),
 * so [KlipyMediaPage.hasNext] maps directly to the next key and the feed is
 * forward-only ([LoadResult.Page.prevKey] is always null). A failed page becomes
 * [LoadResult.Error] so the UI can surface retry.
 */
public class KlipyPagingSource(
    private val fetchPage: suspend (page: Int) -> Result<KlipyMediaPage>,
) : PagingSource<Int, KlipyMediaUi>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, KlipyMediaUi> {
        val page = params.key ?: FIRST_PAGE
        return fetchPage(page).fold(
            onSuccess = { result ->
                LoadResult.Page(
                    data = result.items,
                    prevKey = null,
                    nextKey = if (result.hasNext) page + 1 else null,
                )
            },
            onFailure = { LoadResult.Error(it) },
        )
    }

    // Refresh restarts from the first page — KLIPY feeds have no stable anchor
    // key across an invalidation (a new query/category is a different feed).
    override fun getRefreshKey(state: PagingState<Int, KlipyMediaUi>): Int? = null

    private companion object {
        const val FIRST_PAGE = 1
    }
}
