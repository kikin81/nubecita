package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.RecentSearchRepository
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presenter for the Search tab home (input row + recent-search chips).
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
 *     and the derived [SearchScreenViewState.isQueryBlank].
 *   - [RecentSearchRepository.observeRecent] updates
 *     [SearchScreenViewState.recentSearches].
 *
 * Event handling (submit / chip tap / remove / clear-all) lands in a
 * later step.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class SearchViewModel
    @Inject
    constructor(
        private val recentSearches: RecentSearchRepository,
    ) : MviViewModel<SearchScreenViewState, SearchEvent, SearchEffect>(SearchScreenViewState()) {
        val textFieldState: TextFieldState = TextFieldState()

        init {
            snapshotFlow { textFieldState.text.toString() }
                .debounce(DEBOUNCE.inWholeMilliseconds)
                .distinctUntilChanged()
                .onEach { raw ->
                    val trimmed = raw.trim()
                    setState { copy(currentQuery = trimmed, isQueryBlank = trimmed.isEmpty()) }
                }.launchIn(viewModelScope)

            recentSearches
                .observeRecent()
                .onEach { list -> setState { copy(recentSearches = list.toImmutableList()) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: SearchEvent) {
            // Implemented in the next task.
        }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
