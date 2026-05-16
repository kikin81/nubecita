package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.feature.search.impl.ui.RecentSearchChipStrip
import net.kikin.nubecita.feature.search.impl.ui.SearchInputRow

/**
 * Stateful Search tab home. Hoists [SearchViewModel] and renders the input
 * row + (optionally) the recent-search chip strip. The tab content
 * (Posts / People) is added below this Column in nubecita-vrba.8; this
 * commit ships only the input half.
 */
@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Remember the bound-method reference so SearchScreenContent's onEvent
    // parameter stays stable across recompositions. Mirrors the
    // `:feature:chats:impl` pattern (`onEvent = viewModel::handleEvent` via
    // a remembered reference).
    val onEvent = remember(viewModel) { viewModel::handleEvent }
    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        recentSearches = state.recentSearches,
        onEvent = onEvent,
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Extracted so preview / screenshot-test
 * composables can drive the layout without a Hilt-graph dependency on
 * [SearchViewModel]. The single [onEvent] callback is the stable
 * dispatch seam; per-component callbacks are derived once via
 * `remember` so the leaf composables ([SearchInputRow],
 * [RecentSearchChipStrip]) keep their narrow `(String) -> Unit`
 * contracts without paying for unstable lambda allocations.
 */
@Composable
internal fun SearchScreenContent(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    recentSearches: ImmutableList<String>,
    onEvent: (SearchEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onSubmit = remember(onEvent) { { onEvent(SearchEvent.SubmitClicked) } }
    val onChipTap = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipTapped(query)) } }
    val onChipRemove = remember(onEvent) { { query: String -> onEvent(SearchEvent.RecentChipRemoved(query)) } }
    val onClearAll = remember(onEvent) { { onEvent(SearchEvent.ClearAllRecentsClicked) } }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        SearchInputRow(
            textFieldState = textFieldState,
            isQueryBlank = isQueryBlank,
            onSubmit = onSubmit,
        )
        if (recentSearches.isNotEmpty()) {
            RecentSearchChipStrip(
                items = recentSearches,
                onChipTap = onChipTap,
                onChipRemove = onChipRemove,
                onClearAll = onClearAll,
            )
        }
        // The TabRow + tab content (Posts / People) land in nubecita-vrba.8.
    }
}
