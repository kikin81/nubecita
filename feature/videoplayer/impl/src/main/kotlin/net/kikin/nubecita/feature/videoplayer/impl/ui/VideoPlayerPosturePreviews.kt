@file:OptIn(ExperimentalMediaQueryApi::class)

package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalMediaQueryApi
import androidx.compose.ui.LocalUiMediaScope
import androidx.compose.ui.UiMediaScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerLoadStatus
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState

/**
 * A hand-rolled [UiMediaScope] for previews and screenshot fixtures.
 *
 * The real scope is only provided at a ComposeView root when
 * `ComposeUiFlags.isMediaQueryIntegrationEnabled` was set before that root
 * composed, which a preview host does not do. Delegating to
 * `LocalUiMediaScope.current` (the pattern in the Android docs) therefore
 * throws here, so this implements the whole interface instead.
 */
internal class PreviewUiMediaScope(
    override val windowPosture: UiMediaScope.Posture,
    override val windowWidth: Dp,
    override val windowHeight: Dp,
) : UiMediaScope {
    override val pointerPrecision: UiMediaScope.PointerPrecision = UiMediaScope.PointerPrecision.Coarse
    override val keyboardKind: UiMediaScope.KeyboardKind = UiMediaScope.KeyboardKind.None
    override val hasMicrophone: Boolean = true
    override val hasCamera: Boolean = true
    override val viewingDistance: UiMediaScope.ViewingDistance = UiMediaScope.ViewingDistance.Near
}

/**
 * Renders the Ready player under a simulated window posture. The posture goes
 * through [queryTabletopPosture], the same `mediaQuery` call the screen makes
 * on a device, minus [isTabletopPosture]'s flag guard (the flag is off in a
 * preview host).
 */
@Composable
internal fun VideoPlayerPosturePreview(
    posture: UiMediaScope.Posture,
    windowWidth: Dp = POSTURE_PREVIEW_WIDTH_DP.dp,
    windowHeight: Dp = POSTURE_PREVIEW_HEIGHT_DP.dp,
) {
    val mediaScope =
        remember(posture, windowWidth, windowHeight) {
            PreviewUiMediaScope(
                windowPosture = posture,
                windowWidth = windowWidth,
                windowHeight = windowHeight,
            )
        }
    CompositionLocalProvider(LocalUiMediaScope provides mediaScope) {
        NubecitaTheme(dynamicColor = false) {
            VideoPlayerContent(
                state =
                    VideoPlayerState(
                        loadStatus = VideoPlayerLoadStatus.Ready,
                        // Never loaded: with no image loader in a preview host the
                        // poster slot paints its placeholder color, which is what
                        // makes the video's rectangle visible here. Without it the
                        // Player area is black on black and a fixture could not
                        // tell where the video sits.
                        posterUrl = "preview://poster",
                        aspectRatio = 16f / 9f,
                        isPlaying = true,
                        isMuted = false,
                        positionMs = 5_400L,
                        durationMs = 30_000L,
                        chromeVisible = true,
                    ),
                player = null,
                onEvent = {},
                onPopOut = {},
                isTabletop = queryTabletopPosture(),
            )
        }
    }
}

// An unfolded book-style foldable held in landscape, the orientation tabletop
// posture happens in.
internal const val POSTURE_PREVIEW_WIDTH_DP = 840
internal const val POSTURE_PREVIEW_HEIGHT_DP = 700

@Preview(
    name = "VideoPlayer: flat posture",
    showBackground = true,
    widthDp = POSTURE_PREVIEW_WIDTH_DP,
    heightDp = POSTURE_PREVIEW_HEIGHT_DP,
)
@Composable
private fun VideoPlayerFlatPosturePreview() {
    VideoPlayerPosturePreview(posture = UiMediaScope.Posture.Flat)
}

// A flip-style foldable (flex mode): the fold is horizontal with the phone
// upright, so each half is a short, narrow panel. The tightest case for the
// stacked controls.
internal const val FLIP_PREVIEW_WIDTH_DP = 360
internal const val FLIP_PREVIEW_HEIGHT_DP = 800

@Preview(
    name = "VideoPlayer: tabletop posture, flip phone",
    showBackground = true,
    widthDp = FLIP_PREVIEW_WIDTH_DP,
    heightDp = FLIP_PREVIEW_HEIGHT_DP,
)
@Composable
private fun VideoPlayerTabletopFlipPosturePreview() {
    VideoPlayerPosturePreview(
        posture = UiMediaScope.Posture.Tabletop,
        windowWidth = FLIP_PREVIEW_WIDTH_DP.dp,
        windowHeight = FLIP_PREVIEW_HEIGHT_DP.dp,
    )
}

@Preview(
    name = "VideoPlayer: tabletop posture",
    showBackground = true,
    widthDp = POSTURE_PREVIEW_WIDTH_DP,
    heightDp = POSTURE_PREVIEW_HEIGHT_DP,
)
@Composable
private fun VideoPlayerTabletopPosturePreview() {
    VideoPlayerPosturePreview(posture = UiMediaScope.Posture.Tabletop)
}
