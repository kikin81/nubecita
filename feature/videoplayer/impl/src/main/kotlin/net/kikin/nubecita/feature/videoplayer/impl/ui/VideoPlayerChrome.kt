package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.videoplayer.impl.R
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerEvent
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState
import java.util.Locale

/**
 * Chrome overlay for the fullscreen player. Tap-to-toggle visibility +
 * 3s auto-hide is managed by [VideoPlayerViewModel]; this composable
 * just renders the controls assuming it's visible.
 *
 * Layout: top row (back button), bottom column (seek bar with elapsed
 * /total + transport-row with mute + play/pause).
 */
@Composable
internal fun VideoPlayerChrome(
    state: VideoPlayerState,
    onEvent: (VideoPlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.windowInsetsPadding(WindowInsets.systemBars)) {
        // Top band: back button.
        IconButton(
            onClick = { onEvent(VideoPlayerEvent.BackClicked) },
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(44.dp),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Close,
                contentDescription = stringResource(R.string.video_player_back_content_description),
                tint = Color.White,
            )
        }

        // Bottom band: seek bar + transport row.
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VideoPlayerSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = { onEvent(VideoPlayerEvent.SeekTo(it)) },
                modifier = Modifier.fillMaxWidth(),
            )
            // Transport row: time labels + spacer + mute + play/pause.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatPositionMs(state.positionMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = " / " + formatPositionMs(state.durationMs),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelMedium,
                )
                Box(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onEvent(VideoPlayerEvent.MuteClicked) },
                    modifier = Modifier.size(44.dp),
                ) {
                    NubecitaIcon(
                        name = if (state.isMuted) NubecitaIconName.VolumeOff else NubecitaIconName.VolumeUp,
                        contentDescription =
                            stringResource(
                                if (state.isMuted) {
                                    R.string.video_player_unmute_content_description
                                } else {
                                    R.string.video_player_mute_content_description
                                },
                            ),
                        tint = Color.White,
                    )
                }
                IconButton(
                    onClick = { onEvent(VideoPlayerEvent.PlayPauseClicked) },
                    modifier = Modifier.size(44.dp),
                ) {
                    NubecitaIcon(
                        name = if (state.isPlaying) NubecitaIconName.Pause else NubecitaIconName.PlayArrow,
                        contentDescription =
                            stringResource(
                                if (state.isPlaying) {
                                    R.string.video_player_pause_content_description
                                } else {
                                    R.string.video_player_play_content_description
                                },
                            ),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Seek bar with drag-only commit semantics: `onValueChange` is many
 * times per gesture (every touch frame on Material's Slider), but
 * `seekTo` on Media3 can be expensive for HLS-backed playback (segment
 * fetch, decoder flush). Track the drag fraction locally and only fire
 * [onSeek] on `onValueChangeFinished` so a single scrub gesture
 * produces a single seek.
 */
@Composable
private fun VideoPlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingFraction: Float? by remember { mutableStateOf(null) }
    val seekContentDescription = stringResource(R.string.video_player_seek_content_description)
    val positionFraction =
        if (durationMs > 0L) {
            // Position can outrun duration near EOS or while the player's
            // duration probe lags the position polling tick — Slider crashes
            // if value escapes valueRange, so clamp defensively.
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    Slider(
        value = draggingFraction ?: positionFraction,
        onValueChange = { fraction ->
            if (durationMs > 0L) {
                draggingFraction = fraction.coerceIn(0f, 1f)
            }
        },
        onValueChangeFinished = {
            val committed = draggingFraction
            draggingFraction = null
            if (durationMs > 0L && committed != null) {
                onSeek((committed * durationMs).toLong())
            }
        },
        valueRange = 0f..1f,
        modifier = modifier.semantics { contentDescription = seekContentDescription },
    )
}

/**
 * `m:ss` for clips under an hour, `h:mm:ss` for longer.
 * Mirrors `feature/feed/impl/.../PostCardVideoEmbed.formatDuration`:
 * the format string runs through [Locale.ROOT] so digit shaping stays
 * ASCII (locales like `ar-SA` would otherwise render Eastern Arabic
 * numerals and divergence between devices/CI would break screenshot
 * tests). Operates on milliseconds (the chrome's native unit) instead
 * of seconds.
 */
private fun formatPositionMs(positionMs: Long): String {
    val totalSec = (positionMs / 1_000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

@Composable
internal fun VideoPlayerLoadingBody(modifier: Modifier = Modifier) {
    val loadingContentDescription = stringResource(R.string.video_player_loading_content_description)
    Box(
        modifier =
            modifier
                .background(Color.Black)
                .semantics { contentDescription = loadingContentDescription },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
internal fun VideoPlayerErrorBody(
    error: VideoPlayerError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (titleRes, bodyRes) =
        when (error) {
            VideoPlayerError.Network ->
                R.string.video_player_error_network_title to R.string.video_player_error_network_body
            VideoPlayerError.Decode ->
                R.string.video_player_error_decode_title to R.string.video_player_error_decode_body
            is VideoPlayerError.Unknown ->
                R.string.video_player_error_unknown_title to R.string.video_player_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .background(Color.Black)
                .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(titleRes),
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.video_player_error_retry))
        }
    }
}
