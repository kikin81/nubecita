package net.kikin.nubecita.feature.search.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.search.impl.ui.FeedsTabContent

/**
 * Stateful entry for the Feeds tab. Hoists [SearchFeedsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchFeedsEffect] propagation — append-error snackbar, empty-state
 * clear-query, and nav-to-feed — up to the host screen or
 * [LocalMainShellNavState].
 *
 * Mirrors [SearchActorsScreen]: [SearchFeedsEffect.NavigateToFeed] is
 * collected here and pushes [FeedView] onto the MainShell back stack.
 */
@Composable
internal fun SearchFeedsScreen(
    currentQuery: String,
    fromRecent: Boolean,
    submission: Int,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchFeedsError) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SearchFeedsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    // Key on fromRecent too so a same-query re-submit with a different
    // origin (typed Search after a recent-chip tap) isn't logged stale. A new `submission` is an explicit re-submit
    // of the same text, which must fetch fresh.
    LaunchedEffect(currentQuery, fromRecent, submission) {
        viewModel.setQuery(currentQuery, fromRecent = fromRecent, submission = submission)
    }

    val pinErrorMsg = stringResource(R.string.search_pin_error)
    val unpinErrorMsg = stringResource(R.string.search_unpin_error)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchFeedsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                is SearchFeedsEffect.PinFailed -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(if (effect.wasUnpin) unpinErrorMsg else pinErrorMsg)
                }
                SearchFeedsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
                is SearchFeedsEffect.NavigateToFeed ->
                    navState.add(FeedView(feedUri = effect.uri, displayName = effect.displayName))
            }
        }
    }

    FeedsTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
