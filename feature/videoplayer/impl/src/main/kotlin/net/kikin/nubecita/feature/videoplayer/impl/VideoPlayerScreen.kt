package net.kikin.nubecita.feature.videoplayer.impl

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalComposerSubmitEvents
import net.kikin.nubecita.core.common.navigation.LocalPipController
import net.kikin.nubecita.core.common.navigation.rememberIsInPipMode
import net.kikin.nubecita.core.common.screen.KeepScreenOnWhile
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsEffect
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsEvent
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsSheetContent
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsViewModel
import net.kikin.nubecita.designsystem.adaptive.VideoCommentsSheetScaffold
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.videoplayer.impl.ui.VideoPlayerContent

/**
 * Stateful entry for the fullscreen video player.
 *
 * Hoists [VideoPlayerViewModel] and routes:
 *  - `VideoPlayerEvent` from the chrome composables back to the VM.
 *  - `VideoPlayerEffect.NavigateBack` to a `goBack()` on the OUTER
 *    NavDisplay (`MainNavigation` in `:app`). The route is
 *    `@OuterShell`-qualified per `VideoPlayerNavigationModule`, so
 *    dismissing the player pops the outer back stack back to `Main`,
 *    preserving the inner MainShell tab the user was on.
 *  - Comments sheet presentation via [VideoCommentsSheetScaffold],
 *    wiring cross-shell composer navigation on the outer shell navigator.
 *
 * The [viewModel] argument is required (no `hiltViewModel()` default)
 * because [VideoPlayerViewModel] is assisted-injected with the
 * `VideoPlayerRoute` NavKey — only the entry-provider call site has the
 * route in scope to feed the Factory's `creationCallback`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier,
    commentsViewModel: VideoCommentsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val commentsState by commentsViewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.sharedVideoPlayer.player.collectAsStateWithLifecycle()

    // This route runs in PlaybackMode.Fullscreen — the user is watching, not
    // scrolling past a muted preview, so the screen is held while playing.
    // Feed previews deliberately get no such treatment.
    KeepScreenOnWhile(state.isPlaying)
    val navigator = LocalAppNavigator.current
    val currentNavigator by rememberUpdatedState(navigator)
    val snackbarHostState = remember { SnackbarHostState() }

    val sheetState =
        rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.PartiallyExpanded, SheetValue.Expanded),
        )

    // PiP wiring (design D5): the screen — not the VM — drives the Activity PiP
    // bridge. Republish params whenever play state / aspect / PiP enablement /
    // measured video bounds change so aspect ratio, action, and source rect
    // are up-to-date for explicit PiP entry.
    val pipBridge = LocalPipController.current
    val isInPip by rememberIsInPipMode()
    val pipEnabled by pipBridge.isEnabled.collectAsStateWithLifecycle()
    var sourceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }

    // Instant PiP snap-to-hidden: bypass exit animation so PiP capture window does not snap a collapsing sheet.
    LaunchedEffect(isInPip) {
        if (isInPip) {
            if (state.isCommentsOpen) {
                viewModel.handleEvent(VideoPlayerEvent.DismissComments)
                commentsViewModel.handleEvent(VideoCommentsEvent.DismissComments)
            }
            sheetState.hide()
        }
    }

    // Predictive back gesture: smoothly dismisses sheet when open.
    PredictiveBackHandler(enabled = state.isCommentsOpen && !isInPip) { progress ->
        try {
            progress.collect { /* progress updates */ }
            viewModel.handleEvent(VideoPlayerEvent.DismissComments)
            commentsViewModel.handleEvent(VideoCommentsEvent.DismissComments)
        } catch (_: CancellationException) {
            // Back gesture cancelled
        }
    }

    LaunchedEffect(state.isCommentsOpen) {
        if (state.isCommentsOpen) {
            commentsViewModel.handleEvent(VideoCommentsEvent.OpenComments(viewModel.postUri))
        }
    }

    LaunchedEffect(state.isPlaying, state.aspectRatio, pipEnabled, sourceRect) {
        pipBridge.updateParams(
            aspectRatio = state.aspectRatio,
            isPlaying = state.isPlaying,
            sourceRectHint = sourceRect,
        )
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                VideoPlayerEffect.NavigateBack -> currentNavigator.goBack()
            }
        }
    }

    LaunchedEffect(commentsViewModel) {
        commentsViewModel.effects.collect { effect ->
            when (effect) {
                is VideoCommentsEffect.NavigateToComposer -> {
                    currentNavigator.goTo(ComposerRoute(replyToUri = effect.parentPostUri))
                }

                is VideoCommentsEffect.ShowError -> {
                    launch {
                        snackbarHostState.showSnackbar(effect.message)
                    }
                }
            }
        }
    }

    val composerSubmitEvents = LocalComposerSubmitEvents.current
    val replySentMessage = stringResource(R.string.video_player_reply_sent)
    val postSentMessage = stringResource(R.string.video_player_post_sent)
    LaunchedEffect(composerSubmitEvents, viewModel, commentsViewModel) {
        composerSubmitEvents.collectLatest { event ->
            event.replyToUri?.let { parentUri ->
                viewModel.handleEvent(VideoPlayerEvent.OnReplySubmitted(parentUri))
            }
            commentsViewModel.handleEvent(VideoCommentsEvent.OnComposerSubmitted(event))
            val message = if (event.replyToUri != null) replySentMessage else postSentMessage
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message = message)
        }
    }

    // Explicit pop-out affordance. Shown only where the device
    // supports PiP AND the user has not disabled it in Settings.
    // Tapping directly enters PiP.
    val onPopOut: (() -> Unit)? =
        if (pipBridge.isPipSupported && pipEnabled) {
            {
                viewModel.onPipReach(entered = true)
                pipBridge.enterPip()
            }
        } else {
            null
        }

    val onDismissComments = {
        viewModel.handleEvent(VideoPlayerEvent.DismissComments)
        commentsViewModel.handleEvent(VideoCommentsEvent.DismissComments)
    }

    VideoCommentsSheetScaffold(
        isCommentsOpen = state.isCommentsOpen && !isInPip,
        onDismiss = onDismissComments,
        sheetState = sheetState,
        commentsContent = {
            VideoCommentsSheetContent(
                state = commentsState,
                onLikeClick = { comment ->
                    commentsViewModel.handleEvent(VideoCommentsEvent.LikeComment(comment.id))
                },
                onRetryClick = {
                    commentsViewModel.handleEvent(VideoCommentsEvent.RetryLoad)
                },
                onRefresh = {
                    commentsViewModel.handleEvent(VideoCommentsEvent.Refresh)
                },
                onOpenComposer = {
                    commentsViewModel.handleEvent(VideoCommentsEvent.OpenComposer)
                },
                onAuthorClick = { author ->
                    currentNavigator.goTo(Profile(handle = author.did))
                },
                onCommentClick = { comment ->
                    currentNavigator.goTo(PostDetailRoute(postUri = comment.id))
                },
            )
        },
        videoContent = {
            Scaffold(
                modifier = modifier,
                containerColor = Color.Black,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                VideoPlayerContent(
                    state = state,
                    player = player,
                    onEvent = viewModel::handleEvent,
                    isInPip = isInPip,
                    onSourceRectChange = { sourceRect = it },
                    onPopOut = onPopOut,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }
        },
    )
}
