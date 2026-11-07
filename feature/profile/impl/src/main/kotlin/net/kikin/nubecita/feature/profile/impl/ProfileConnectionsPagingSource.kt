package net.kikin.nubecita.feature.profile.impl

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.kikin.nubecita.feature.profile.impl.data.ConnectionPage
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi

/**
 * Network cursor source over one followers or following list. Key = the opaque
 * cursor; the first page uses a null key.
 *
 * Drops accounts already served by this source. The list is keyed by DID, and a
 * repeated key crashes a `LazyColumn`, so a follow landing between two page
 * fetches must not be able to take the screen down.
 */
internal class ProfileConnectionsPagingSource(
    private val loadPage: suspend (cursor: String?) -> Result<ConnectionPage>,
) : PagingSource<String, ConnectionRowUi>() {
    private val seen = HashSet<String>()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, ConnectionRowUi> =
        loadPage(params.key).fold(
            onSuccess = { page ->
                LoadResult.Page(
                    data = page.rows.filter { seen.add(it.actor.did) },
                    prevKey = null,
                    nextKey = page.cursor,
                )
            },
            onFailure = { LoadResult.Error(it) },
        )

    // A cursor source has no stable anchor key; restart from the head on refresh.
    override fun getRefreshKey(state: PagingState<String, ConnectionRowUi>): String? = null
}
