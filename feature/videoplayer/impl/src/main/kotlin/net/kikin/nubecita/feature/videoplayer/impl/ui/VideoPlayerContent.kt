@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@file:OptIn(ExperimentalGridApi::class)

package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerEvent
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerLoadStatus
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState
import kotlin.math.roundToInt

/**
 * Stateless body. Branches on `state.loadStatus`:
 *  - [VideoPlayerLoadStatus.Idle] / [VideoPlayerLoadStatus.Resolving]:
 *    centered CircularProgressIndicator over black.
 *  - [VideoPlayerLoadStatus.Ready]: a single `Grid` with two named areas
 *    ([VideoPlayerArea.Player], [VideoPlayerArea.Controller]). Flat
 *    posture maps both to one cell, giving the three Z-layers of the
 *    surface-composition rule: poster image (lowest) → Media3
 *    PlayerSurface (middle) → AnimatedVisibility chrome overlay (top).
 *    Tabletop posture ([isTabletop]) maps them to two half-height rows:
 *    video above the fold, controls below it. The children are the same
 *    composables in both, so folding only relayouts and the surface is
 *    never recreated. See [VideoPlayerGridConfiguration].
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
    // In PiP the window is just the video — chrome is suppressed. Defaults keep
    // previews/screenshot fixtures rendering the normal (non-PiP) surface.
    isInPip: Boolean = false,
    onSourceRectChange: (android.graphics.Rect) -> Unit = {},
    // Pop-out / enter-PiP handler for the chrome (nubecita-q5ge.8); null hides
    // the affordance (non-PiP device, previews).
    onPopOut: (() -> Unit)? = null,
    // Tabletop posture, resolved by the stateful screen via mediaQuery. A plain
    // parameter (not a mediaQuery call in here) so previews and screenshot
    // fixtures need no LocalUiMediaScope.
    isTabletop: Boolean = false,
    // The host Scaffold's inner padding (system bars). Applied per grid area
    // rather than around the whole grid: the grid has to span the full window
    // for its 50/50 tabletop split to land on the fold. Padding the grid as a
    // whole shifts the split by half the difference between the top and bottom
    // insets (12dp on a Pixel Fold, where the status bar is taller than the
    // gesture bar).
    contentPadding: PaddingValues = PaddingValues(),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val layoutDirection = LocalLayoutDirection.current
    // Flat: both areas share one cell, so both take the full padding and the
    // result is the same as padding the parent. Tabletop: each area keeps only
    // the edges that touch the window. The edge on the fold gets none.
    val playerPadding =
        remember(contentPadding, layoutDirection, isTabletop) {
            if (isTabletop) {
                PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = 0.dp,
                )
            } else {
                contentPadding
            }
        }
    val controllerPadding =
        remember(contentPadding, layoutDirection, isTabletop) {
            if (isTabletop) {
                PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    top = 0.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding(),
                )
            } else {
                contentPadding
            }
        }

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
                VideoPlayerLoadingBody(modifier = Modifier.fillMaxSize().padding(contentPadding))
            VideoPlayerLoadStatus.Ready -> {
                // Picked in composition and handed to Grid as a stable lambda.
                // Grid reads it in the measure pass, so a posture change is a
                // relayout of the same two children, not a recomposition that
                // could tear down the PlayerSurface.
                val gridConfig =
                    if (isTabletop) VideoPlayerGridConfiguration.Tabletop else VideoPlayerGridConfiguration.Default
                Grid(
                    config = gridConfig,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Surface composition rule: poster (lowest) → player
                    // (middle) → chrome (top, via Grid declaration order).
                    // The poster + player share an aspect-ratio Box centered
                    // in the Player area so the rendered video matches the
                    // source frame shape. PlayerSurface itself fills its
                    // bounds without an intrinsic aspect-fit. The rest of the
                    // area shows through to the root Box's black bg as
                    // letterbox bars.
                    Box(
                        modifier =
                            Modifier
                                .gridItem(areaId = VideoPlayerArea.Player, alignment = Alignment.Center)
                                .padding(playerPadding)
                                // Tag lives on the READY-state surface only (not the
                                // root Box) so the screengrab journey's await fails
                                // loudly on a resolver/playback error instead of
                                // green-lighting the "Something went wrong" screen.
                                .testTag("video_player")
                                .then(
                                    if (state.aspectRatio != null) {
                                        // No fillMaxSize in front: the Grid cell
                                        // hands down loose constraints, which lets
                                        // aspectRatio fit INSIDE the cell on either
                                        // axis. Under tight constraints it only
                                        // ever fits the width and overflows a cell
                                        // that is wider than the video (the
                                        // half-height tabletop row, or a portrait
                                        // clip in a landscape window).
                                        Modifier.aspectRatio(state.aspectRatio)
                                    } else {
                                        Modifier.fillMaxSize()
                                    },
                                )
                                // Report the video's on-screen bounds so the PiP enter
                                // animation morphs from the actual surface, not the whole window.
                                .onGloballyPositioned { onSourceRectChange(it.boundsInWindow().toAndroidRectInt()) },
                    ) {
                        if (state.posterUrl != null) {
                            NubecitaAsyncImage(
                                model = state.posterUrl,
                                contentDescription = state.altText,
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
                    }
                    AnimatedVisibility(
                        // Chrome (back button, seek bar, controls) is always hidden in
                        // PiP: the floating window shows only the video. In tabletop
                        // it is pinned visible instead. It has its own half of the
                        // screen there and covers nothing, so auto-hide would only
                        // leave the flat half empty.
                        visible = (state.chromeVisible || isTabletop) && !isInPip,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier =
                            Modifier
                                .gridItem(areaId = VideoPlayerArea.Controller)
                                .fillMaxSize()
                                .padding(controllerPadding)
                                .testTag("video_player_controller"),
                    ) {
                        VideoPlayerChrome(
                            state = state,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxSize(),
                            onPopOut = onPopOut,
                            stacked = isTabletop,
                        )
                    }
                }
            }
            is VideoPlayerLoadStatus.Error ->
                VideoPlayerErrorBody(
                    error = state.loadStatus.error,
                    onRetry = { onEvent(VideoPlayerEvent.RetryClicked) },
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                )
        }
    }
}

private fun androidx.compose.ui.geometry.Rect.toAndroidRectInt(): android.graphics.Rect = android.graphics.Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())

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
