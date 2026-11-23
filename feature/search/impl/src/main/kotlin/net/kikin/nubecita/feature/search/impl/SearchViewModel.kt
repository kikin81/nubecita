package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.RecentSearchRepository
import net.kikin.nubecita.feature.search.impl.data.SearchTab
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearch
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearchStore
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presenter for the Search tab home (input row + chip strip / typeahead / results).
 *
 * Owns a public [textFieldState] per the sanctioned editor exception
 * (see `SearchContract.kt` KDoc + `:feature:composer:impl`'s
 * `ComposerViewModel`). The screen Composable wires
 * `OutlinedTextField(state = vm.textFieldState, ...)` directly so IME
 * writes don't round-trip through `handleEvent`.
 *
 * What was submitted lives in [SubmittedSearchStore], not here, so the
 * result tabs (and anything outside this screen) read the same value. The
 * body [SearchScreenViewState.phase] is derived from the store's `submitted`
 * value, so the two can never disagree. A search belongs to this ViewModel:
 * it starts with none ([SubmittedSearchStore.clear] in `init`, which drops a
 * search a previous instance left behind) and ends with none (`onCleared`).
 *
 * `init` launches three collectors:
 *   - `snapshotFlow { textFieldState.text.toString() }.debounce(DEBOUNCE)
 *     .distinctUntilChanged()` updates [SearchScreenViewState.currentQuery]
 *     (which drives the expanded overlay's typeahead) and clears the store when
 *     the field goes blank.
 *   - the store's `submitted` flow updates [SearchScreenViewState.phase].
 *   - [RecentSearchRepository.observeRecent] updates
 *     [SearchScreenViewState.recentSearches].
 *
 * The submit paths ([SearchEvent.SubmitClicked] and
 * [SearchEvent.RecentChipTapped]) write the store and flip the phase
 * synchronously, so the body doesn't flicker through a stale Discover frame
 * while the 250ms debounce window catches up. Typeahead is an overlay
 * concern, driven by the live [SearchScreenViewState.currentQuery], not a
 * body phase.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class SearchViewModel
    @Inject
    constructor(
        private val recentSearches: RecentSearchRepository,
        private val submittedSearch: SubmittedSearchStore,
    ) : MviViewModel<SearchScreenViewState, SearchEvent, SearchEffect>(SearchScreenViewState()) {
        val textFieldState: TextFieldState = TextFieldState()

        init {
            submittedSearch.clear()

            submittedSearch.submitted
                .onEach { submitted -> setState { followingSubmitted(submitted) } }
                .launchIn(viewModelScope)

            snapshotFlow { textFieldState.text.toString() }
                .debounce(DEBOUNCE.inWholeMilliseconds)
                .distinctUntilChanged()
                .onEach { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) {
                        submittedSearch.clear()
                    }
                    setState {
                        copy(
                            currentQuery = trimmed,
                            isQueryBlank = trimmed.isEmpty(),
                        ).followingSubmitted(submittedSearch.submitted.value)
                    }
                }.launchIn(viewModelScope)

            recentSearches
                .observeRecent()
                .onEach { list -> setState { copy(recentSearches = list.toImmutableList()) } }
                .launchIn(viewModelScope)
        }

        override fun onCleared() {
            submittedSearch.clear()
            super.onCleared()
        }

        override fun handleEvent(event: SearchEvent) {
            when (event) {
                SearchEvent.SubmitClicked -> persistCurrent(fromRecent = false)
                is SearchEvent.RecentChipTapped -> {
                    textFieldState.setTextAndPlaceCursorAtEnd(event.query)
                    persistCurrent(fromRecent = true)
                }
                is SearchEvent.RecentChipRemoved ->
                    viewModelScope.launch {
                        recentSearches.remove(event.query)
                    }
                SearchEvent.ClearAllRecentsClicked ->
                    viewModelScope.launch {
                        recentSearches.clearAll()
                    }
                is SearchEvent.TabSelected -> submittedSearch.selectTab(event.tab)
                SearchEvent.ClearSearch -> clearSearch()
            }
        }

        private fun persistCurrent(fromRecent: Boolean) {
            val text = textFieldState.text.toString().trim()
            if (text.isEmpty()) return
            submittedSearch.submit(text, fromRecent = fromRecent)
            // Atomic phase flip so the body switches to Results immediately
            // on submit rather than waiting for the store collector or the
            // debounced currentQuery emission; both compute the same value
            // from the store and are no-ops.
            setState {
                copy(
                    currentQuery = text,
                    isQueryBlank = false,
                ).followingSubmitted(submittedSearch.submitted.value)
            }
            viewModelScope.launch { recentSearches.record(text) }
        }

        private fun clearSearch() {
            textFieldState.clearText()
            submittedSearch.clear()
            setState {
                copy(
                    currentQuery = "",
                    isQueryBlank = true,
                ).followingSubmitted(submittedSearch.submitted.value)
            }
        }

        /**
         * The body beneath the collapsed search bar shows the result tabs
         * for the submitted search, or the Discover (recent chips) surface
         * when there is none. Typeahead is not a body state, so the phase
         * depends solely on the store, not on the in-flight text.
         */
        private fun SearchScreenViewState.followingSubmitted(submitted: SubmittedSearch?): SearchScreenViewState = copy(phase = submitted.toPhase(), selectedTab = submitted?.selectedTab ?: SearchTab.POSTS)

        private fun SubmittedSearch?.toPhase(): SearchPhase =
            if (this == null) {
                SearchPhase.Discover
            } else {
                SearchPhase.Results(query = query, fromRecent = fromRecent, submission = submission)
            }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
