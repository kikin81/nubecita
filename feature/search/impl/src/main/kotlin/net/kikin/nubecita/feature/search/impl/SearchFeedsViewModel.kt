package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.SearchFeedsRepository
import javax.inject.Inject

/**
 * Presenter for the Search Feeds tab. Reactive on a
 * [MutableStateFlow] of [FetchKey] — the screen Composable's
 * `LaunchedEffect(parentState.currentQuery)` calls [setQuery] whenever
 * the parent [SearchViewModel] emits a new debounced query.
 *
 * Shape mirrors [SearchActorsViewModel]: blank-handling lives inside
 * `mapLatest` so a blank emission cancels in-flight fetches and resets
 * `loadStatus` to `Idle`; [SearchFeedsEvent.Retry] bumps an incarnation
 * token so the pipeline fires even when the query hasn't changed;
 * `loadMore` carries a stale-completion guard against late page
 * completions after the user typed past the boundary.
 *
 * Does NOT inject the parent [SearchViewModel] — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is the
 * orchestration seam.
 *
 * No tap-through event in V1: `FeedRow` ships without a `clickable`
 * modifier until `:feature:feeddetail:api` exists. When the route
 * lands, add a `FeedTapped(uri)` event + a
 * `SearchFeedsEffect.NavigateToFeed(uri)` emission here, and the
 * screen Composable collects it the same way the Posts and People tabs
 * handle their nav effects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchFeedsViewModel
    @Inject
    constructor(
        private val repository: SearchFeedsRepository,
    ) : MviViewModel<SearchFeedsState, SearchFeedsEvent, SearchFeedsEffect>(SearchFeedsState()) {
        private data class FetchKey(
            val query: String,
            /** Bumps on [SearchFeedsEvent.Retry] to force a re-emit when query didn't change. */
            val incarnation: Int,
        )

        private val fetchKey = MutableStateFlow(FetchKey(query = "", incarnation = 0))

        init {
            fetchKey
                .onEach { key -> setState { copy(currentQuery = key.query) } }
                .mapLatest { key ->
                    if (key.query.isBlank()) {
                        setState { copy(loadStatus = SearchFeedsLoadStatus.Idle) }
                    } else {
                        runFirstPage(key)
                    }
                }.launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect(parent.currentQuery)`. */
        fun setQuery(query: String) {
            fetchKey.update { it.copy(query = query) }
        }

        override fun handleEvent(event: SearchFeedsEvent) {
            when (event) {
                SearchFeedsEvent.LoadMore -> loadMore()
                SearchFeedsEvent.Retry ->
                    fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                SearchFeedsEvent.ClearQueryClicked ->
                    sendEffect(SearchFeedsEffect.NavigateToClearQuery)
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchFeedsLoadStatus.InitialLoading) }
            repository
                .searchFeeds(query = key.query, cursor = null)
                .onSuccess { page ->
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchFeedsLoadStatus.Empty
                        } else {
                            SearchFeedsLoadStatus.Loaded(
                                items = page.items,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchFeedsLoadStatus.InitialError(throwable.toSearchFeedsError()))
                    }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchFeedsLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            val capturedKey = fetchKey.value
            viewModelScope.launch {
                repository
                    .searchFeeds(query = capturedKey.query, cursor = cursor)
                    .onSuccess { page ->
                        if (fetchKey.value != capturedKey) return@onSuccess
                        val current = uiState.value.loadStatus as? SearchFeedsLoadStatus.Loaded ?: return@onSuccess
                        val appended = (current.items + page.items).toImmutableList()
                        setState {
                            copy(
                                loadStatus =
                                    current.copy(
                                        items = appended,
                                        nextCursor = page.nextCursor,
                                        endReached = page.nextCursor == null,
                                        isAppending = false,
                                    ),
                            )
                        }
                    }.onFailure { throwable ->
                        if (fetchKey.value != capturedKey) return@onFailure
                        val current = uiState.value.loadStatus as? SearchFeedsLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchFeedsEffect.ShowAppendError(throwable.toSearchFeedsError()))
                    }
            }
        }
    }
