package net.kikin.nubecita.feature.videoplayer.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerLoadStatus
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState

/**
 * Screenshot baselines for [VideoPlayerContent] covering every
 * `VideoPlayerLoadStatus` variant the screen renders × light/dark:
 *
 * - **Resolving**: centered progress indicator over black.
 * - **Ready playing**: chrome visible, pause icon active, seek bar
 *   mid-track. Player param is null — layoutlib can't construct
 *   `PlayerSurface`, and `VideoPlayerContent` skips it when player is
 *   null so the layout still renders the poster + chrome layers.
 * - **Ready paused**: same as playing but `isPlaying = false`; locks
 *   the play-icon swap.
 * - **Ready chrome hidden**: chrome `AnimatedVisibility` collapsed —
 *   guards against an accidental "always visible" regression that
 *   would block the video underneath.
 * - **Error**: centered error layout with Retry button.
 *
 * The fixed `heightDp` matches the search-feature screenshot tests so
 * the fullscreen surface renders against a phone-shaped canvas
 * instead of layoutlib's default infinite height.
 */

private const val CANVAS_HEIGHT_DP = 600

@PreviewTest
@Preview(name = "resolving-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "resolving-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentResolvingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state = VideoPlayerState(loadStatus = VideoPlayerLoadStatus.Resolving),
            player = null,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-playing-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-playing-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyPlayingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
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

@PreviewTest
@Preview(name = "ready-paused-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-paused-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyPausedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
                    isPlaying = false,
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

@PreviewTest
@Preview(name = "ready-chrome-hidden-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-chrome-hidden-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyChromeHiddenScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
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

@PreviewTest
@Preview(name = "error-network-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "error-network-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentErrorScreenshot() {
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
