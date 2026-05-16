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
 *     and recomputes [SearchScreenViewState.phase] against the internal
 *     [submittedQuery] so typing after a submit returns to
 *     [SearchPhase.Typeahead] until the next submission.
 *   - [RecentSearchRepository.observeRecent] updates
 *     [SearchScreenViewState.recentSearches].
 *
 * [submittedQuery] is intentionally a private field (not in state): the
 * UI never branches on "what was last submitted"; it only branches on
 * the derived [SearchPhase]. Submission paths ([SearchEvent.SubmitClicked]
 * and [SearchEvent.RecentChipTapped]) update it synchronously and
 * atomically flip [SearchScreenViewState.phase] to [SearchPhase.Results]
 * so the body doesn't flicker through a stale Typeahead frame while the
 * 250ms debounce window catches up.
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
         * Cleared back to `""` when the field goes blank so re-typing the
         * same string after a clear lands in Typeahead first, not Results.
         */
        private var submittedQuery: String = ""

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
                            phase = phaseFor(trimmed),
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
                SearchEvent.SubmitClicked -> persistCurrent()
                is SearchEvent.RecentChipTapped -> {
                    textFieldState.setTextAndPlaceCursorAtEnd(event.query)
                    persistCurrent()
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

        private fun persistCurrent() {
            val text = textFieldState.text.toString().trim()
            if (text.isEmpty()) return
            submittedQuery = text
            // Atomic phase flip so the body doesn't render Typeahead for
            // one frame between the synchronous submit and the debounced
            // currentQuery emission. The debounced emission that follows
            // computes the same SearchPhase.Results value and is a no-op.
            setState {
                copy(
                    currentQuery = text,
                    isQueryBlank = false,
                    phase = SearchPhase.Results(text),
                )
            }
            viewModelScope.launch { recentSearches.record(text) }
        }

        private fun phaseFor(trimmed: String): SearchPhase =
            when {
                trimmed.isEmpty() -> SearchPhase.Discover
                trimmed == submittedQuery -> SearchPhase.Results(trimmed)
                else -> SearchPhase.Typeahead(trimmed)
            }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
