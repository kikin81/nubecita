package net.kikin.nubecita.feature.videoplayer.impl.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.videoplayer.impl.ChromeVisibility
import net.kikin.nubecita.feature.videoplayer.impl.R
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerEvent
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState
import java.util.Locale

/**
 * Chrome overlay for the fullscreen player. Renders one of three
 * [ChromeVisibility] rungs — the VM drives the Shown → Peeking → Hidden
 * auto-hide ladder (nubecita-6rdb.7):
 *
 *  - **Shown**: full scrim + back button + transport cluster (skip ·
 *    morphing play/pause · skip) + mute/PiP utility row + seek bar + time.
 *  - **Peeking**: lighter scrim + back button + seek bar + time; the
 *    center transport cluster is dropped so the video is less obscured
 *    while the user can still scrub.
 *  - **Hidden**: no scrim — just a centered tap-to-play button and a thin
 *    bottom progress hairline.
 *
 * Per-element [AnimatedVisibility] cross-fades the rungs; the scrim alpha
 * animates between them.
 */
@Composable
internal fun VideoPlayerChrome(
    state: VideoPlayerState,
    onEvent: (VideoPlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
    // The pop-out / enter-PiP affordance (nubecita-q5ge.8). Null hides it — the
    // screen passes a handler only on PiP-capable devices. The handler itself
    // (PiP entry for Pro, paywall for non-Pro) lives in the screen, not the VM
    // (design D5), so it's a plain callback rather than a VideoPlayerEvent.
    onPopOut: (() -> Unit)? = null,
) {
    val visibility = state.chromeVisibility
    val controlsShown = visibility == ChromeVisibility.Shown
    val chromeShown = visibility != ChromeVisibility.Hidden
    val hidden = visibility == ChromeVisibility.Hidden

    Box(modifier = modifier) {
        // Scrim spans edge-to-edge (under the system bars); the controls below
        // respect the safe insets.
        VideoPlayerScrim(visibility = visibility, modifier = Modifier.fillMaxSize())

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            // Back button — Shown + Peeking.
            AnimatedVisibility(
                visible = chromeShown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                IconButton(
                    onClick = { onEvent(VideoPlayerEvent.BackClicked) },
                    modifier = Modifier.padding(8.dp).size(44.dp),
                ) {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.video_player_back_content_description),
                        tint = Color.White,
                    )
                }
            }

            // Transport cluster + mute/PiP utility row — Shown only.
            AnimatedVisibility(
                visible = controlsShown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                TransportCluster(state = state, onEvent = onEvent, onPopOut = onPopOut)
            }

            // Hidden rung: a single centered tap-to-play button.
            AnimatedVisibility(
                visible = hidden,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                HiddenPlayButton(state = state, onEvent = onEvent)
            }

            // Bottom band (seek bar + time) — Shown + Peeking.
            AnimatedVisibility(
                visible = chromeShown,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                BottomBand(state = state, onEvent = onEvent)
            }

            // Hidden rung: a thin bottom progress hairline.
            AnimatedVisibility(
                visible = hidden,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                ProgressHairline(positionMs = state.positionMs, durationMs = state.durationMs)
            }
        }
    }
}

/**
 * Dark vertical-gradient scrim behind the controls so the white-on-video
 * chrome stays legible. Alpha animates by rung — full at Shown, lighter at
 * Peeking, gone at Hidden.
 */
@Composable
private fun VideoPlayerScrim(
    visibility: ChromeVisibility,
    modifier: Modifier = Modifier,
) {
    val scrimAlpha by animateFloatAsState(
        targetValue =
            when (visibility) {
                ChromeVisibility.Shown -> 1f
                ChromeVisibility.Peeking -> 0.5f
                ChromeVisibility.Hidden -> 0f
            },
        label = "scrimAlpha",
    )
    // Constant gradient — remembered so the per-frame recomposition while
    // scrimAlpha animates doesn't re-allocate the brush.
    val scrimBrush =
        remember {
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.3f to Color.Transparent,
                0.7f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.65f),
            )
        }
    Box(modifier = modifier.alpha(scrimAlpha).background(scrimBrush))
}

/**
 * The Shown-rung transport: skip-back 10s · large morphing play/pause ·
 * skip-forward 10s, with a mute + PiP utility row beneath the play/pause
 * (nubecita-6rdb.6, design option a). The pop-out shows only on PiP-capable
 * devices (`onPopOut != null`); otherwise mute sits alone, centered. PiP
 * keeps its device × Pro gating + resolvePopOut routing (design D5).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportCluster(
    state: VideoPlayerState,
    onEvent: (VideoPlayerEvent) -> Unit,
    onPopOut: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // Remembered so the morph button's `shapes` is a stable instance —
    // IconButtonShapes isn't @Stable, so allocating it inline would defeat
    // skipping and re-run the button on every position tick (120hz target).
    val playPauseShapes =
        remember {
            IconButtonShapes(
                shape = CircleShape,
                pressedShape = RoundedCornerShape(PLAY_PAUSE_PRESSED_CORNER),
            )
        }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = { onEvent(VideoPlayerEvent.SkipBack) },
                modifier = Modifier.size(SKIP_BUTTON_SIZE),
                shape = CircleShape,
                colors = translucentSkipColors(),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.Replay10,
                    contentDescription = stringResource(R.string.video_player_skip_back_content_description),
                    opticalSize = SKIP_ICON_SIZE,
                    tint = Color.White,
                )
            }
            // The one element that breaks from the surrounding circular shape
            // language: a large filled primary button that morphs round→squircle
            // on press (design panel C).
            FilledIconButton(
                onClick = { onEvent(VideoPlayerEvent.PlayPauseClicked) },
                modifier = Modifier.size(PLAY_PAUSE_BUTTON_SIZE),
                shapes = playPauseShapes,
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
                    opticalSize = PLAY_PAUSE_ICON_SIZE,
                )
            }
            FilledIconButton(
                onClick = { onEvent(VideoPlayerEvent.SkipForward) },
                modifier = Modifier.size(SKIP_BUTTON_SIZE),
                shape = CircleShape,
                colors = translucentSkipColors(),
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.Forward10,
                    contentDescription = stringResource(R.string.video_player_skip_forward_content_description),
                    opticalSize = SKIP_ICON_SIZE,
                    tint = Color.White,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
            if (onPopOut != null) {
                IconButton(
                    onClick = onPopOut,
                    modifier = Modifier.size(44.dp),
                ) {
                    NubecitaIcon(
                        name = NubecitaIconName.PictureInPictureAlt,
                        contentDescription = stringResource(R.string.video_player_pip_content_description),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * The Hidden-rung tap-to-play: a single translucent play/pause button,
 * centered. Less prominent than the Shown rung's primary-filled morph
 * button — the Hidden rung is "get out of the way of the video".
 */
@Composable
private fun HiddenPlayButton(
    state: VideoPlayerState,
    onEvent: (VideoPlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledIconButton(
        onClick = { onEvent(VideoPlayerEvent.PlayPauseClicked) },
        modifier = modifier.size(PLAY_PAUSE_BUTTON_SIZE),
        shape = CircleShape,
        colors = translucentSkipColors(),
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
            opticalSize = PLAY_PAUSE_ICON_SIZE,
            tint = Color.White,
        )
    }
}

/** Bottom band for the Shown + Peeking rungs: wavy seek bar + time labels. */
@Composable
private fun BottomBand(
    state: VideoPlayerState,
    onEvent: (VideoPlayerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
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
        // Time labels: elapsed left, total right (nubecita-6rdb.6).
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPositionMs(state.positionMs),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
            )
            Box(modifier = Modifier.weight(1f))
            Text(
                text = formatPositionMs(state.durationMs),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * The Hidden-rung progress hairline: a thin 2dp bar pinned to the bottom
 * edge showing the played fraction, so the user keeps a sense of position
 * even with the full chrome hidden.
 */
@Composable
private fun ProgressHairline(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val fraction =
        if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.3f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Color.White),
        )
    }
}

// Centered transport cluster sizing. The play/pause is intentionally larger
// than the skip buttons so it reads as the primary control; the pressed corner
// drives the round→squircle morph.
private val SKIP_BUTTON_SIZE = 52.dp
private val SKIP_ICON_SIZE = 28.dp
private val PLAY_PAUSE_BUTTON_SIZE = 72.dp
private val PLAY_PAUSE_ICON_SIZE = 36.dp
private val PLAY_PAUSE_PRESSED_CORNER = 27.dp

/**
 * Translucent dark-on-white fill for the skip buttons (design token
 * `rgba(255,255,255,0.16)` over the scrim) with a white glyph — distinct
 * from the primary-filled play/pause so the hierarchy reads correctly.
 */
@Composable
private fun translucentSkipColors() =
    IconButtonDefaults.filledIconButtonColors(
        containerColor = Color.White.copy(alpha = 0.16f),
        contentColor = Color.White,
    )

/**
 * Wavy seek bar (design D3): a Material 3 `Slider` whose track slot is a
 * [LinearWavyProgressIndicator] — the played portion renders as a wave, the
 * remaining portion as a flat track. The slider keeps the thumb, gesture, and
 * accessibility; only the track painting changes, so the drag-to-commit
 * semantics are unchanged.
 *
 * Drag-only commit: `onValueChange` fires many times per gesture (every touch
 * frame), but `seekTo` on Media3 can be expensive for HLS-backed playback
 * (segment fetch, decoder flush). Track the drag fraction locally and only
 * fire [onSeek] on `onValueChangeFinished` so a single scrub gesture produces a
 * single seek. The wave amplitude eases to 0 while scrubbing so the playhead
 * reads cleanly, then eases back.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    // Flatten the wave (amplitude → 0) while the user scrubs, then ease it back.
    val waveAmplitude by animateFloatAsState(
        targetValue = if (draggingFraction != null) 0f else 1f,
        label = "seekBarAmplitude",
    )
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
        colors = SliderDefaults.colors(thumbColor = Color.White),
        track = { sliderState ->
            LinearWavyProgressIndicator(
                progress = { sliderState.value },
                modifier = Modifier.fillMaxWidth(),
                // Played wave = primary, matching M3 Expressive's default
                // indicator color and the design (the play button is primary
                // too). The thumb stays white so the playhead reads against the
                // wave; the remaining track is a light line over the scrim.
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.3f),
                amplitude = { waveAmplitude },
            )
        },
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
