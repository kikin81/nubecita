package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        recentSearches = state.recentSearches,
        onSubmit = { viewModel.handleEvent(SearchEvent.SubmitClicked) },
        onChipTap = { viewModel.handleEvent(SearchEvent.RecentChipTapped(it)) },
        onChipRemove = { viewModel.handleEvent(SearchEvent.RecentChipRemoved(it)) },
        onClearAll = { viewModel.handleEvent(SearchEvent.ClearAllRecentsClicked) },
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Extracted so preview / screenshot-test
 * composables can drive the layout without a Hilt-graph dependency on
 * [SearchViewModel].
 */
@Composable
internal fun SearchScreenContent(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    recentSearches: ImmutableList<String>,
    onSubmit: () -> Unit,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
