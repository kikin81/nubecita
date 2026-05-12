package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.designsystem.component.PostCallbacks

/**
 * Stateful Profile screen.
 *
 * Owns the screen's lifecycle wiring: state collection, the single
 * effects collector that surfaces snackbars + nav callbacks, the
 * LazyListState hoisted via rememberSaveable (for back-nav and
 * config-change retention), the screen-internal SnackbarHostState,
 * and the remember-d PostCallbacks that dispatch to the VM.
 * Delegates rendering to [ProfileScreenContent], which previews and
 * screenshot tests call directly with fixture inputs.
 */
@Composable
internal fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToPost: (String) -> Unit,
    onNavigateToProfile: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }

    val callbacks =
        remember(viewModel) {
            PostCallbacks(
                onTap = { post -> viewModel.handleEvent(ProfileEvent.PostTapped(post.id)) },
                onAuthorTap = { author ->
                    viewModel.handleEvent(ProfileEvent.HandleTapped(author.handle))
                },
                // Bead D ships read-only — like/repost/reply/share are no-ops here.
                // Wire to real handlers when the Profile epic adds writes.
                onLike = {},
                onRepost = {},
                onReply = {},
                onShare = {},
                onShareLongPress = {},
                onExternalEmbedTap = {},
            )
        }

    // Pre-resolve snackbar copy via stringResource() so locale changes
    // recompose. Reading via context.getString() inside the LaunchedEffect
    // would bypass Compose's resource tracking (LocalContextGetResourceValueCall).
    val errorNetworkMsg = stringResource(R.string.profile_snackbar_error_network)
    val errorUnknownMsg = stringResource(R.string.profile_snackbar_error_unknown)
    val comingSoonEdit = stringResource(R.string.profile_snackbar_edit_coming_soon)
    val comingSoonFollow = stringResource(R.string.profile_snackbar_follow_coming_soon)
    val comingSoonMessage = stringResource(R.string.profile_snackbar_message_coming_soon)

    // Wrap nav callbacks so the long-lived effect collector keys on Unit
    // (one collector for the screen's lifetime) but always calls the most
    // recent lambda the host supplied.
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToProfile by rememberUpdatedState(onNavigateToProfile)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ProfileError.Network -> errorNetworkMsg
                            is ProfileError.Unknown -> errorUnknownMsg
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.ShowComingSoon -> {
                    val msg =
                        when (effect.action) {
                            StubbedAction.Edit -> comingSoonEdit
                            StubbedAction.Follow -> comingSoonFollow
                            StubbedAction.Message -> comingSoonMessage
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is ProfileEffect.NavigateToProfile -> currentOnNavigateToProfile(effect.handle)
                ProfileEffect.NavigateToSettings -> {
                    // Bead F wires the Settings push.
                }
            }
        }
    }

    ProfileScreenContent(
        state = state,
        listState = listState,
        snackbarHostState = snackbarHostState,
        postCallbacks = callbacks,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
