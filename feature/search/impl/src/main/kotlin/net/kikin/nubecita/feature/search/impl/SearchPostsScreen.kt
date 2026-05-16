package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort
import net.kikin.nubecita.feature.search.impl.ui.PostsTabContent

/**
 * Stateful entry for the Posts tab. Hoists [SearchPostsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchPostsEffect.NavigateToPost] to [LocalMainShellNavState] —
 * mirroring `:feature:chats:impl/ChatScreen`'s effect-collection
 * pattern.
 *
 * Two effects propagate via callback up to the (future) vrba.8
 * search-results screen:
 *  - [onClearQuery]: parent SearchViewModel owns the canonical
 *    TextFieldState and is the only thing that can reset it.
 *  - [onShowAppendError]: append-time failures surface as snackbars
 *    in the host's SnackbarHostState, which lives at the search-
 *    screen level (not inside the per-tab content).
 */
@Composable
internal fun SearchPostsScreen(
    currentQuery: String,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchPostsError) -> Unit,
    modifier: Modifier = Modifier,
    initialSort: SearchPostsSort = SearchPostsSort.TOP,
    viewModel: SearchPostsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    // Push the latest debounced query down to the VM. The VM
    // dedupes via StateFlow operator fusion on the FetchKey.
    LaunchedEffect(currentQuery) {
        viewModel.setQuery(currentQuery)
    }

    // initialSort is fired once on screen entry — the user's
    // subsequent SortClicked events drive sort changes from inside the
    // VM, so we don't re-LaunchedEffect on this past first composition.
    LaunchedEffect(Unit) {
        if (initialSort != SearchPostsSort.TOP) {
            viewModel.handleEvent(SearchPostsEvent.SortClicked(initialSort))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchPostsEffect.NavigateToPost ->
                    navState.add(PostDetailRoute(postUri = effect.uri))
                is SearchPostsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                SearchPostsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
            }
        }
    }

    PostsTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
