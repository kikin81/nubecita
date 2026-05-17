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
import net.kikin.nubecita.core.common.haptic.rememberPostHaptics
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
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
    onNavigateToSettings: () -> Unit,
    onNavigateToMessage: (String) -> Unit,
    onNavigateToMediaViewer: (postUri: String, imageIndex: Int) -> Unit,
    onNavigateToVideoPlayer: (postUri: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }

    val haptics = rememberPostHaptics()
    val callbacks =
        remember(viewModel, haptics) {
            PostCallbacks(
                onTap = { post -> viewModel.handleEvent(ProfileEvent.PostTapped(post.id)) },
                onAuthorTap = { author ->
                    viewModel.handleEvent(ProfileEvent.HandleTapped(author.handle))
                },
                onLike = { post ->
                    if (post.viewer.isLikedByViewer) haptics.likeOff() else haptics.likeOn()
                    viewModel.handleEvent(ProfileEvent.OnLikeClicked(post))
                },
                onRepost = { post ->
                    if (post.viewer.isRepostedByViewer) haptics.repostOff() else haptics.repostOn()
                    viewModel.handleEvent(ProfileEvent.OnRepostClicked(post))
                },
                onReply = {},
                onShare = {},
                onShareLongPress = {},
                onExternalEmbedTap = {},
                onQuotedPostTap = { quoted ->
                    viewModel.handleEvent(ProfileEvent.OnQuotedPostTapped(quoted.uri))
                },
            )
        }

    // Pre-resolve snackbar copy via stringResource() so locale changes
    // recompose. Reading via context.getString() inside the LaunchedEffect
    // would bypass Compose's resource tracking (LocalContextGetResourceValueCall).
    val errorNetworkMsg = stringResource(R.string.profile_snackbar_error_network)
    val errorUnknownMsg = stringResource(R.string.profile_snackbar_error_unknown)
    val comingSoonEdit = stringResource(R.string.profile_snackbar_edit_coming_soon)
    val comingSoonBlock = stringResource(R.string.profile_snackbar_block_coming_soon)
    val comingSoonMute = stringResource(R.string.profile_snackbar_mute_coming_soon)
    val comingSoonReport = stringResource(R.string.profile_snackbar_report_coming_soon)

    // Wrap nav callbacks so the long-lived effect collector keys on Unit
    // (one collector for the screen's lifetime) but always calls the most
    // recent lambda the host supplied.
    val currentOnNavigateToPost by rememberUpdatedState(onNavigateToPost)
    val currentOnNavigateToProfile by rememberUpdatedState(onNavigateToProfile)
    val currentOnNavigateToSettings by rememberUpdatedState(onNavigateToSettings)
    val currentOnNavigateToMessage by rememberUpdatedState(onNavigateToMessage)
    val currentOnNavigateToMediaViewer by rememberUpdatedState(onNavigateToMediaViewer)
    val currentOnNavigateToVideoPlayer by rememberUpdatedState(onNavigateToVideoPlayer)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProfileEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ProfileError.Network -> errorNetworkMsg
                            is ProfileError.Unknown -> errorUnknownMsg
                        }
                    // ProfileEffect.ShowError is most often a toggle / follow
                    // failure (user-tap rejected). Over-fires slightly on
                    // background header-refresh failures, which is acceptable
                    // noise — header refresh is rare and a reject buzz on a
                    // failed sync isn't misleading.
                    haptics.rejected()
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.ShowComingSoon -> {
                    val msg =
                        when (effect.action) {
                            StubbedAction.Edit -> comingSoonEdit
                            StubbedAction.Block -> comingSoonBlock
                            StubbedAction.Mute -> comingSoonMute
                            StubbedAction.Report -> comingSoonReport
                        }
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = msg)
                }
                is ProfileEffect.NavigateToPost -> currentOnNavigateToPost(effect.postUri)
                is ProfileEffect.NavigateToProfile -> currentOnNavigateToProfile(effect.handle)
                ProfileEffect.NavigateToSettings -> currentOnNavigateToSettings()
                is ProfileEffect.NavigateToMessage ->
                    currentOnNavigateToMessage(effect.otherUserDid)
                is ProfileEffect.NavigateToMediaViewer ->
                    currentOnNavigateToMediaViewer(effect.postUri, effect.imageIndex)
                is ProfileEffect.NavigateToVideoPlayer ->
                    currentOnNavigateToVideoPlayer(effect.postUri)
            }
        }
    }

    val mainShellNavState = LocalMainShellNavState.current
    val onBack: (() -> Unit)? =
        if (state.handle != null) {
            { mainShellNavState.removeLast() }
        } else {
            null
        }

    ProfileScreenContent(
        state = state,
        listState = listState,
        snackbarHostState = snackbarHostState,
        postCallbacks = callbacks,
        onEvent = viewModel::handleEvent,
        onBack = onBack,
        modifier = modifier,
    )
}
