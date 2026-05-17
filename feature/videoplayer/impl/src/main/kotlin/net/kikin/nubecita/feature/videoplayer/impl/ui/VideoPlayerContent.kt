@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerEvent
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerLoadStatus
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState

/**
 * Stateless body. Branches on `state.loadStatus`:
 *  - [VideoPlayerLoadStatus.Idle] / [VideoPlayerLoadStatus.Resolving]:
 *    centered CircularProgressIndicator over black.
 *  - [VideoPlayerLoadStatus.Ready]: three Z-layers per the surface-
 *    composition rule: poster image (lowest) → Media3 PlayerSurface
 *    (middle) → AnimatedVisibility chrome overlay (top). Stacking
 *    ensures a smooth poster reveal during PlayerSurface attach/detach
 *    transitions instead of a black flash.
 *  - [VideoPlayerLoadStatus.Error]: centered title/body + retry button.
 *
 * Tap-to-toggle chrome is delivered as `VideoPlayerEvent.ToggleChrome`
 * via the outer Box's clickable modifier. The [MutableInteractionSource]
 * uses `indication = null` so there's no ripple on the video surface.
 */
@Composable
internal fun VideoPlayerContent(
    state: VideoPlayerState,
    player: androidx.media3.common.Player?,
    onEvent: (VideoPlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = { onEvent(VideoPlayerEvent.ToggleChrome) },
                ),
    ) {
        when (state.loadStatus) {
            VideoPlayerLoadStatus.Idle, VideoPlayerLoadStatus.Resolving ->
                VideoPlayerLoadingBody(modifier = Modifier.fillMaxSize())
            VideoPlayerLoadStatus.Ready -> {
                // Surface composition rule: poster (lowest) → player (middle) → chrome (top).
                if (state.posterUrl != null) {
                    NubecitaAsyncImage(
                        model = state.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
                if (player != null) {
                    PlayerSurface(
                        player = player,
                        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                AnimatedVisibility(
                    visible = state.chromeVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    VideoPlayerChrome(
                        state = state,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            is VideoPlayerLoadStatus.Error ->
                VideoPlayerErrorBody(
                    error = state.loadStatus.error,
                    onRetry = { onEvent(VideoPlayerEvent.RetryClicked) },
                    modifier = Modifier.fillMaxSize(),
                )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "VideoPlayer — Resolving",
    showBackground = true,
)
@Composable
private fun VideoPlayerContentResolvingPreview() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state = VideoPlayerState(loadStatus = VideoPlayerLoadStatus.Resolving),
            player = null,
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "VideoPlayer — Ready chrome visible",
    showBackground = true,
)
@Composable
private fun VideoPlayerContentReadyChromeVisiblePreview() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    isPlaying = true,
                    isMuted = false,
                    positionMs = 5_400L,
                    durationMs = 30_000L,
                    chromeVisible = true,
                ),
            player = null,
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "VideoPlayer — Ready chrome hidden",
    showBackground = true,
)
@Composable
private fun VideoPlayerContentReadyChromeHiddenPreview() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    isPlaying = true,
                    isMuted = true,
                    positionMs = 12_000L,
                    durationMs = 30_000L,
                    chromeVisible = false,
                ),
            player = null,
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(
    name = "VideoPlayer — Error network",
    showBackground = true,
)
@Composable
private fun VideoPlayerContentErrorPreview() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Error(error = VideoPlayerError.Network),
                ),
            player = null,
            onEvent = {},
        )
    }
}
