package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.feature.search.impl.ui.FeedsTabContent

/**
 * Stateful entry for the Feeds tab. Hoists [SearchFeedsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchFeedsEffect] propagation — append-error snackbar + empty-state
 * clear-query — up to the host screen.
 *
 * No `LocalMainShellNavState` hookup yet — [SearchFeedsEffect] doesn't
 * carry a nav variant. `SearchFeedsEvent.FeedTapped` is a no-op in the
 * VM today (no feed-detail feature exists); when that lands, add
 * `data class NavigateToFeed(val uri: String)` to [SearchFeedsEffect]
 * and collect it here the same way [SearchPostsScreen] and
 * [SearchActorsScreen] handle their nav effects.
 */
@Composable
internal fun SearchFeedsScreen(
    currentQuery: String,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchFeedsError) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchFeedsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    LaunchedEffect(currentQuery) {
        viewModel.setQuery(currentQuery)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchFeedsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                SearchFeedsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
            }
        }
    }

    FeedsTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
