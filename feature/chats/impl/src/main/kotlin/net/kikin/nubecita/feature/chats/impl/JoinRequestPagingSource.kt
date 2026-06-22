package net.kikin.nubecita.feature.chats.impl

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository

/**
 * Network cursor [PagingSource] over [ChatRepository.getJoinRequests]. Key = the opaque cursor
 * string; the first page uses a null key.
 */
internal class JoinRequestPagingSource(
    private val convoId: String,
    private val repository: ChatRepository,
) : PagingSource<String, JoinRequestUi>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, JoinRequestUi> =
        repository.getJoinRequests(convoId, params.key).fold(
            onSuccess = { page ->
                LoadResult.Page(data = page.requests, prevKey = null, nextKey = page.cursor)
            },
            onFailure = { LoadResult.Error(it) },
        )

    // A cursor source has no stable anchor key; restart from the head on refresh.
    override fun getRefreshKey(state: PagingState<String, JoinRequestUi>): String? = null
}
