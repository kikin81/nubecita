package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.SearchPostsRepository
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import javax.inject.Inject

/**
 * Presenter for the Search Posts tab. Reactive on a
 * [MutableStateFlow] of [FetchKey] — the screen Composable's
 * `LaunchedEffect(parentState.currentQuery)` calls [setQuery] whenever
 * the parent `SearchViewModel` emits a new debounced query, and
 * `SortClicked` / `Retry` update the key from inside [handleEvent].
 *
 * The init pipeline runs:
 *
 *   fetchKey
 *     .onEach { setState(currentQuery, sort) }
 *     .filter { it.query.isNotBlank() }
 *     .mapLatest { runFirstPage(it) }
 *     .launchIn(viewModelScope)
 *
 * StateFlow's operator fusion already dedupes identical consecutive
 * values, so we skip an explicit `distinctUntilChanged()` (which the
 * compiler also flags as deprecated for StateFlow). `mapLatest` cancels
 * the prior in-flight fetch on a new key — the canonical pattern from
 * `:feature:composer:impl`'s `ComposerViewModel` typeahead path. Retry
 * bumps an internal incarnation token so the pipeline fires even when
 * query + sort haven't changed.
 *
 * Does NOT inject the parent `SearchViewModel` — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is the
 * orchestration seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchPostsViewModel
    @Inject
    constructor(
        private val repository: SearchPostsRepository,
    ) : MviViewModel<SearchPostsState, SearchPostsEvent, SearchPostsEffect>(SearchPostsState()) {
        private data class FetchKey(
            val query: String,
            val sort: SearchPostsSort,
            /** Bumps on [SearchPostsEvent.Retry] to force a re-emit when query+sort didn't change. */
            val incarnation: Int,
        )

        private val fetchKey =
            MutableStateFlow(FetchKey(query = "", sort = SearchPostsSort.TOP, incarnation = 0))

        init {
            fetchKey
                .onEach { key ->
                    setState { copy(currentQuery = key.query, sort = key.sort) }
                }.filter { it.query.isNotBlank() }
                .mapLatest { key -> runFirstPage(key) }
                .launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect(parent.currentQuery)`. */
        fun setQuery(query: String) {
            fetchKey.update { it.copy(query = query) }
        }

        override fun handleEvent(event: SearchPostsEvent) {
            when (event) {
                SearchPostsEvent.LoadMore -> loadMore()
                SearchPostsEvent.Retry ->
                    fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                SearchPostsEvent.ClearQueryClicked ->
                    sendEffect(SearchPostsEffect.NavigateToClearQuery)
                is SearchPostsEvent.SortClicked ->
                    fetchKey.update { it.copy(sort = event.sort, incarnation = it.incarnation + 1) }
                is SearchPostsEvent.PostTapped ->
                    sendEffect(SearchPostsEffect.NavigateToPost(event.uri))
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchPostsLoadStatus.InitialLoading) }
            repository
                .searchPosts(query = key.query, cursor = null, sort = key.sort)
                .onSuccess { page ->
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchPostsLoadStatus.Empty
                        } else {
                            SearchPostsLoadStatus.Loaded(
                                items = page.items,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchPostsLoadStatus.InitialError(throwable.toSearchPostsError()))
                    }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchPostsLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            val sort = uiState.value.sort
            val query = uiState.value.currentQuery
            viewModelScope.launch {
                repository
                    .searchPosts(query = query, cursor = cursor, sort = sort)
                    .onSuccess { page ->
                        val current = uiState.value.loadStatus as? SearchPostsLoadStatus.Loaded ?: return@onSuccess
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
                        val current = uiState.value.loadStatus as? SearchPostsLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchPostsEffect.ShowAppendError(throwable.toSearchPostsError()))
                    }
            }
        }

        private companion object {
            const val TAG = "SearchPostsVM"
        }
    }
