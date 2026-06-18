package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.text.input.TextFieldState
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
 * `init` launches two collectors:
 *   - `snapshotFlow { textFieldState.text.toString() }.debounce(DEBOUNCE)
 *     .distinctUntilChanged()` updates [SearchScreenViewState.currentQuery]
 *     (which drives the expanded overlay's typeahead) and recomputes the
 *     body [SearchScreenViewState.phase] from [submittedQuery].
 *   - [RecentSearchRepository.observeRecent] updates
 *     [SearchScreenViewState.recentSearches].
 *
 * [submittedQuery] is intentionally a private field (not in state): the
 * UI never branches on "what was last submitted"; it only branches on
 * the derived [SearchPhase]. Submission paths ([SearchEvent.SubmitClicked]
 * and [SearchEvent.RecentChipTapped]) update it synchronously and
 * atomically flip [SearchScreenViewState.phase] to [SearchPhase.Results]
 * so the body doesn't flicker through a stale Discover frame while the
 * 250ms debounce window catches up. Typeahead is an overlay concern,
 * driven by the live [SearchScreenViewState.currentQuery], not a body phase.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class SearchViewModel
    @Inject
    constructor(
        private val recentSearches: RecentSearchRepository,
    ) : MviViewModel<SearchScreenViewState, SearchEvent, SearchEffect>(SearchScreenViewState()) {
        val textFieldState: TextFieldState = TextFieldState()

        /**
         * The query the user most recently submitted (IME, CTA, chip tap).
         * Cleared back to `""` when the field goes blank so the body returns
         * to Discover after a clear, rather than showing stale results.
         */
        private var submittedQuery: String = ""

        /** Whether [submittedQuery] came from a recent-search chip tap. Sticky
         * until the next submit so the debounced re-emission of `phaseForBody()`
         * preserves it (carried into `search_perform`'s `from_recent`). */
        private var submittedFromRecent: Boolean = false

        init {
            snapshotFlow { textFieldState.text.toString() }
                .debounce(DEBOUNCE.inWholeMilliseconds)
                .distinctUntilChanged()
                .onEach { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) {
                        submittedQuery = ""
                    }
                    setState {
                        copy(
                            currentQuery = trimmed,
                            isQueryBlank = trimmed.isEmpty(),
                            phase = phaseForBody(),
                        )
                    }
                }.launchIn(viewModelScope)

            recentSearches
                .observeRecent()
                .onEach { list -> setState { copy(recentSearches = list.toImmutableList()) } }
                .launchIn(viewModelScope)
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
            }
        }

        private fun persistCurrent(fromRecent: Boolean) {
            val text = textFieldState.text.toString().trim()
            if (text.isEmpty()) return
            submittedQuery = text
            submittedFromRecent = fromRecent
            // Atomic phase flip so the body switches to Results immediately
            // on submit rather than waiting for the debounced currentQuery
            // emission. The debounced emission that follows computes the same
            // SearchPhase.Results value and is a no-op.
            setState {
                copy(
                    currentQuery = text,
                    isQueryBlank = false,
                    phase = SearchPhase.Results(text, fromRecent = fromRecent),
                )
            }
            viewModelScope.launch { recentSearches.record(text) }
        }

        /**
         * The body beneath the collapsed search bar shows the result tabs
         * for the last submitted query, or the Discover (recent chips)
         * surface when nothing has been submitted. Typeahead is no longer a
         * body state — it lives only in the expanded overlay, driven by the
         * live [SearchScreenViewState.currentQuery]. So the body phase depends
         * solely on [submittedQuery], not on the in-flight text.
         */
        private fun phaseForBody(): SearchPhase =
            if (submittedQuery.isEmpty()) {
                SearchPhase.Discover
            } else {
                SearchPhase.Results(submittedQuery, fromRecent = submittedFromRecent)
            }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
