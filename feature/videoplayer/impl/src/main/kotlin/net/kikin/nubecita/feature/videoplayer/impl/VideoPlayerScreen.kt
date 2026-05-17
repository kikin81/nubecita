package net.kikin.nubecita.feature.videoplayer.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalAppNavigator
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
        modifier = modifier,
    )
}
