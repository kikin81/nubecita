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
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.ui.PeopleTabContent

/**
 * Stateful entry for the People tab. Hoists [SearchActorsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchActorsEffect.NavigateToProfile] to [LocalMainShellNavState] —
 * mirroring `SearchPostsScreen`'s pattern.
 *
 * [SearchActorsEffect.FollowFailed] is shown on the shared [snackbarHostState]
 * here, where the localized copy resolves. Two effects propagate via callback
 * up to the (future) vrba.8 search-results screen:
 *  - [onClearQuery]: parent SearchViewModel owns the canonical
 *    TextFieldState and is the only thing that can reset it.
 *  - [onShowAppendError]: append-time failures surface as snackbars
 *    in the host's SnackbarHostState, which lives at the search-
 *    screen level (not inside the per-tab content).
 */
@Composable
internal fun SearchActorsScreen(
    currentQuery: String,
    fromRecent: Boolean,
    submission: Int,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchActorsError) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: SearchActorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    // Push the latest debounced query down to the VM. The VM
    // dedupes via StateFlow operator fusion on the FetchKey. Key on
    // fromRecent too so a same-query re-submit with a different origin
    // (typed Search after a recent-chip tap) isn't logged stale. A new `submission` is an explicit re-submit
    // of the same text, which must fetch fresh.
    LaunchedEffect(currentQuery, fromRecent, submission) {
        viewModel.setQuery(currentQuery, fromRecent = fromRecent, submission = submission)
    }

    val followErrorMsg = stringResource(R.string.search_follow_error)
    val unfollowErrorMsg = stringResource(R.string.search_unfollow_error)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchActorsEffect.NavigateToProfile ->
                    navState.add(Profile(handle = effect.handle))
                is SearchActorsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                is SearchActorsEffect.FollowFailed ->
                    // Launched, not awaited: a visible snackbar must not hold up the next effect.
                    launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(if (effect.wasUnfollow) unfollowErrorMsg else followErrorMsg)
                    }
                SearchActorsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
            }
        }
    }

    PeopleTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
