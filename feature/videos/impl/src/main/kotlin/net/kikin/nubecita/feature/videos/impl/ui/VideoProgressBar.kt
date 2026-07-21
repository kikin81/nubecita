package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import net.kikin.nubecita.feature.videos.impl.VideoFeedTestTags

/**
 * Drives [VideoProgressBarContent] from the active Media3 [player].
 *
 * A `withFrameNanos` loop writes the live fraction into a
 * [androidx.compose.runtime.MutableFloatState] that ONLY the content's
 * `drawBehind` reads — so each frame re-runs the draw phase and never
 * composition. The loop runs only while [player] is non-null and [isPlaying];
 * when either flips the `LaunchedEffect` cancels and the last value stays drawn
 * (a static bar under pause). `withFrameNanos` also stops producing frames when
 * the app is backgrounded, so the loop is free off-screen.
 *
 * Loop reset is automatic: the fraction is recomputed from the player's live
 * `currentPosition`/`duration` every frame, so when `REPEAT_MODE_ONE` wraps the
 * position back to ~0 the next frame reflects it — nothing is accumulated.
 */
@Composable
internal fun VideoProgressBar(
    player: Player?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player, isPlaying) {
        if (player != null && isPlaying) {
            while (true) {
                withFrameNanos { }
                progress = progressFraction(player.currentPosition, player.duration)
            }
        }
    }
    VideoProgressBarContent(progress = { progress }, modifier = modifier)
}

/**
 * Stateless visual for the playback progress bar: a pill-shaped translucent-white
 * track with a solid-white fill grown left→right to [progress].
 *
 * [progress] is a DEFERRED read — invoked inside the `drawBehind` lambda, never
 * unwrapped at composition scope — so an advancing bar re-runs only the draw
 * phase, never composition or layout. This is the same discipline as
 * `VideoFeedPage`'s poster alpha and `LikeBurst`. Splitting the visual from the
 * frame driver ([VideoProgressBar]) also lets layoutlib screenshot it without a
 * running `withFrameNanos` loop.
 *
 * White-on-black is deliberate: the feed canvas is always `Color.Black`, so the
 * bar does not read theme colours (a themed token would pin a colour that never
 * ships on this surface).
 */
@Composable
internal fun VideoProgressBarContent(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = PROGRESS_INSET)
            .height(PROGRESS_HEIGHT)
            .testTag(VideoFeedTestTags.PROGRESS_BAR)
            .drawBehind {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(color = PROGRESS_TRACK_COLOR, cornerRadius = radius)
                val fillWidth = size.width * progress().coerceIn(0f, 1f)
                if (fillWidth > 0f) {
                    drawRoundRect(
                        color = PROGRESS_FILL_COLOR,
                        size = Size(fillWidth, size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

private val PROGRESS_HEIGHT = 3.dp
private val PROGRESS_INSET = 16.dp
private val PROGRESS_TRACK_COLOR = Color.White.copy(alpha = 0.28f)
private val PROGRESS_FILL_COLOR = Color.White
