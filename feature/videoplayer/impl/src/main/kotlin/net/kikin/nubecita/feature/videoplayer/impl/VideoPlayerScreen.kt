package net.kikin.nubecita.feature.videoplayer.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
import net.kikin.nubecita.core.common.navigation.LocalPipController
import net.kikin.nubecita.core.common.navigation.rememberIsInPipMode
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
 *
 * The [viewModel] argument is required (no `hiltViewModel()` default)
 * because [VideoPlayerViewModel] is assisted-injected with the
 * `VideoPlayerRoute` NavKey — only the entry-provider call site has the
 * route in scope to feed the Factory's `creationCallback`.
 */
@Composable
internal fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.sharedVideoPlayer.player.collectAsStateWithLifecycle()
    val navigator = LocalAppNavigator.current
    val currentNavigator by rememberUpdatedState(navigator)

    // PiP wiring (design D5): the screen — not the VM — drives the Activity PiP
    // bridge. Republish params whenever play state / aspect / entitlement /
    // measured video bounds change, so auto-enter (API 31+) is armed only while
    // enabled + playing and disarms when Pro lapses.
    val pipBridge = LocalPipController.current
    val isInPip by rememberIsInPipMode()
    val pipEnabled by pipBridge.isEnabled.collectAsStateWithLifecycle()
    var sourceRect by remember { mutableStateOf<android.graphics.Rect?>(null) }

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

    VideoPlayerContent(
        state = state,
        player = player,
        onEvent = viewModel::handleEvent,
        isInPip = isInPip,
        onSourceRectChange = { sourceRect = it },
        modifier = modifier,
    )
}
