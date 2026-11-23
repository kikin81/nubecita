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
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.SearchPerform
import net.kikin.nubecita.core.analytics.SearchScope
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.SearchFirstPages
import net.kikin.nubecita.feature.search.impl.data.SearchStarterPacksRepository
import javax.inject.Inject

/**
 * Presenter for the Search Packs tab. Same shape as [SearchFeedsViewModel], minus the pin: a
 * [MutableStateFlow] of [FetchKey] driven by the screen's `LaunchedEffect`, a blank query that
 * cancels in-flight work and resets to `Idle`, [SearchPacksEvent.Retry] bumping an incarnation
 * so the pipeline re-fires for an unchanged query, and a stale-completion guard on `loadMore`.
 * The first page comes through [SearchFirstPages] (shared with the Summary); later pages go
 * straight to the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchPacksViewModel
    @Inject
    constructor(
        private val repository: SearchStarterPacksRepository,
        private val firstPages: SearchFirstPages,
        private val analytics: AnalyticsClient,
    ) : MviViewModel<SearchPacksState, SearchPacksEvent, SearchPacksEffect>(SearchPacksState()) {
        private data class FetchKey(
            val query: String,
            /** Bumps on [SearchPacksEvent.Retry] to force a re-emit when the query did not change. */
            val incarnation: Int,
            /** Whether the query arrived via a recent-search tap (for search_perform). */
            val fromRecent: Boolean,
            /** The store's submission counter: an explicit re-submit of the same text is a new search. */
            val submission: Int,
        )

        private val fetchKey = MutableStateFlow(FetchKey(query = "", incarnation = 0, fromRecent = false, submission = 0))

        init {
            fetchKey
                .onEach { key -> setState { copy(currentQuery = key.query) } }
                .mapLatest { key ->
                    if (key.query.isBlank()) {
                        setState { copy(loadStatus = SearchPacksLoadStatus.Idle) }
                    } else {
                        runFirstPage(key)
                    }
                }.launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect` on the submitted search. */
        fun setQuery(
            query: String,
            fromRecent: Boolean = false,
            submission: Int,
        ) {
            fetchKey.update { it.copy(query = query, fromRecent = fromRecent, submission = submission) }
        }

        override fun handleEvent(event: SearchPacksEvent) {
            when (event) {
                SearchPacksEvent.LoadMore -> loadMore()
                SearchPacksEvent.Retry -> fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                SearchPacksEvent.ClearQueryClicked -> sendEffect(SearchPacksEffect.NavigateToClearQuery)
                is SearchPacksEvent.PackTapped -> sendEffect(SearchPacksEffect.NavigateToPack(event.uri))
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchPacksLoadStatus.InitialLoading) }
            firstPages
                .packs(query = key.query, submission = key.submission)
                .onSuccess { page ->
                    // search_perform for the Packs scope (incl. empty results); the query is never attached.
                    analytics.log(SearchPerform(scope = SearchScope.Packs, fromRecent = key.fromRecent))
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchPacksLoadStatus.Empty
                        } else {
                            SearchPacksLoadStatus.Loaded(
                                items = page.items.distinctBy { it.uri }.toImmutableList(),
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState { copy(loadStatus = SearchPacksLoadStatus.InitialError(throwable.toSearchPacksError())) }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchPacksLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            val capturedKey = fetchKey.value
            viewModelScope.launch {
                repository
                    .searchStarterPacks(query = capturedKey.query, cursor = cursor)
                    .onSuccess { page ->
                        if (fetchKey.value != capturedKey) return@onSuccess
                        val current = uiState.value.loadStatus as? SearchPacksLoadStatus.Loaded ?: return@onSuccess
                        val appended = (current.items + page.items).distinctBy { it.uri }.toImmutableList()
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
                        val current = uiState.value.loadStatus as? SearchPacksLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchPacksEffect.ShowAppendError(throwable.toSearchPacksError()))
                    }
            }
        }
    }
