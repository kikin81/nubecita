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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.videoplayer.impl.R
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerEvent
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState

/**
 * Chrome overlay for the fullscreen player. Tap-to-toggle visibility +
 * 3s auto-hide is managed by [VideoPlayerViewModel]; this composable
 * just renders the controls assuming it's visible.
 *
 * Layout: top row (back button + spacer), spacer, bottom column
 * (seek bar with elapsed/total + transport-row with mute + play/pause).
 * Both bands sit on a black-to-transparent scrim for legibility over
 * busy video frames.
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
            // Seek bar (renders even at Resolving — Slider with 0..0 is OK).
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
                    text = state.positionMs.toMmSs(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    text = " / " + state.durationMs.toMmSs(),
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

@Composable
private fun VideoPlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value =
            if (durationMs > 0L) {
                positionMs.toFloat() / durationMs.toFloat()
            } else {
                0f
            },
        onValueChange = { fraction ->
            if (durationMs > 0L) onSeek((fraction * durationMs).toLong())
        },
        valueRange = 0f..1f,
        modifier = modifier,
    )
}

private fun Long.toMmSs(): String {
    val totalSec = (this / 1_000L).coerceAtLeast(0L)
    val minutes = totalSec / 60L
    val seconds = totalSec % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Composable
internal fun VideoPlayerLoadingBody(modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
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
