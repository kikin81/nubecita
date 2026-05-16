package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.posting.ActorTypeaheadRepository
import javax.inject.Inject

/**
 * Presenter for the Search typeahead screen (vrba.10).
 *
 * Reactive on a [MutableStateFlow] of `query: String` — the screen
 * Composable's `LaunchedEffect(parentState.currentQuery)` calls
 * [setQuery] whenever the parent [SearchViewModel] emits a new
 * debounced query (the parent already owns the 250ms text-input
 * debounce; this VM does not re-debounce).
 *
 * The init pipeline mirrors [SearchActorsViewModel]'s
 * `setQuery + mapLatest` shape:
 *
 *   queryFlow
 *     .onEach { setState(currentQuery) }
 *     .mapLatest { q -> if (blank) reset to Idle else runFetch(q) }
 *     .launchIn(viewModelScope)
 *
 * `mapLatest` cancels the prior in-flight fetch on a new query, and
 * the blank branch lives INSIDE `mapLatest` so a blank emission ALSO
 * cancels any in-flight `runFetch` and resets `status` to `Idle` —
 * same lesson as the SearchActorsViewModel blank-handling shape.
 *
 * Failure handling: per the [ActorTypeaheadRepository] contract,
 * transient failures collapse to [SearchTypeaheadStatus.Idle]
 * (typeahead is QoL — a flaky-network snackbar on every keystroke
 * is more annoying than helpful, and the user can still commit via
 * the always-rendered "Search for {q}" CTA).
 *
 * Does NOT inject the parent [SearchViewModel] — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is
 * the orchestration seam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchTypeaheadViewModel
    @Inject
    constructor(
        private val repository: ActorTypeaheadRepository,
    ) : MviViewModel<SearchTypeaheadState, SearchTypeaheadEvent, SearchTypeaheadEffect>(SearchTypeaheadState()) {
        private val queryFlow = MutableStateFlow("")

        init {
            queryFlow
                .onEach { query -> setState { copy(currentQuery = query) } }
                .mapLatest { query ->
                    if (query.isBlank()) {
                        setState { copy(status = SearchTypeaheadStatus.Idle) }
                    } else {
                        runFetch(query)
                    }
                }.launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect(parent.currentQuery)`. */
        fun setQuery(query: String) {
            queryFlow.value = query
        }

        override fun handleEvent(event: SearchTypeaheadEvent) {
            when (event) {
                is SearchTypeaheadEvent.ActorTapped ->
                    sendEffect(SearchTypeaheadEffect.NavigateToProfile(event.handle))
            }
        }

        private suspend fun runFetch(query: String) {
            setState { copy(status = SearchTypeaheadStatus.Loading(query)) }
            repository
                .searchTypeahead(query)
                .onSuccess { actors ->
                    val nextStatus =
                        if (actors.isEmpty()) {
                            SearchTypeaheadStatus.NoResults(query)
                        } else {
                            SearchTypeaheadStatus.Suggestions(
                                query = query,
                                topMatch = actors.first(),
                                people = actors.drop(1).toImmutableList(),
                            )
                        }
                    setState { copy(status = nextStatus) }
                }.onFailure {
                    // Per contract: collapse to Idle. Sourcing the failure as
                    // an empty Suggestions list would lie about success; the
                    // CTA already gives the user a way forward.
                    setState { copy(status = SearchTypeaheadStatus.Idle) }
                }
        }
    }
