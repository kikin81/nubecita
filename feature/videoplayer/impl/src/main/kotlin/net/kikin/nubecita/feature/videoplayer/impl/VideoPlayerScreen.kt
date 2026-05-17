package net.kikin.nubecita.feature.videoplayer.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.videoplayer.impl.ui.VideoPlayerContent

/**
 * Stateful entry for the fullscreen video player.
 *
 * Hoists [VideoPlayerViewModel] and routes:
 *  - `VideoPlayerEvent` from the chrome composables back to the VM.
 *  - `VideoPlayerEffect.NavigateBack` to a back-pop on the inner
 *    MainShell NavDisplay (mirrors `SearchPostsScreen`'s pattern for
 *    nav effects from a feature impl).
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
    val navState = LocalMainShellNavState.current
    val currentNavState by rememberUpdatedState(navState)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                VideoPlayerEffect.NavigateBack -> currentNavState.removeLast()
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
