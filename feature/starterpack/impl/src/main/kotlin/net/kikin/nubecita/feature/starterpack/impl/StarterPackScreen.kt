package net.kikin.nubecita.feature.starterpack.impl

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.starterpack.impl.ui.StarterPackContent

/**
 * Stateful starter-pack screen. Collects [StarterPackViewModel] state and routes
 * [StarterPackEffect]s: member and feed taps push onto [LocalMainShellNavState] (the screen is
 * itself a detail-pane sub-route, so what it opens stacks above it), and failures surface as
 * snackbars.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun StarterPackScreen(
    viewModel: StarterPackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentNavState by rememberUpdatedState(navState)
    val snackbarHostState = remember { SnackbarHostState() }

    val appendNetwork = stringResource(R.string.starterpack_append_error_network)
    val appendUnknown = stringResource(R.string.starterpack_append_error_unknown)
    val followError = stringResource(R.string.starterpack_follow_error)
    val unfollowError = stringResource(R.string.starterpack_unfollow_error)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StarterPackEffect.NavigateToProfile -> currentNavState.add(Profile(handle = effect.actor))
                is StarterPackEffect.NavigateToFeed ->
                    currentNavState.add(FeedView(feedUri = effect.uri, displayName = effect.displayName))
                is StarterPackEffect.ShowAppendError ->
                    launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(if (effect.error == StarterPackError.Network) appendNetwork else appendUnknown)
                    }
                is StarterPackEffect.FollowFailed ->
                    launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(if (effect.wasUnfollow) unfollowError else followError)
                    }
            }
        }
    }

    StarterPackContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
