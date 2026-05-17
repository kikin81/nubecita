package net.kikin.nubecita.feature.videoplayer.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the fullscreen video player.
 *
 * Flat fields ([isMuted], [chromeVisible], [positionMs], [durationMs])
 * are independent and can change orthogonally. [loadStatus] is the
 * mutually-exclusive lifecycle sum per CLAUDE.md's MVI carve-out:
 * Idle / Resolving / Ready / Error.
 *
 * [posterUrl] carries the resolved post's video poster image so the
 * Content composable can render `NubecitaAsyncImage(posterUrl)` UNDER
 * the PlayerSurface — per the design's "surface composition rule"
 * that says PlayerSurface must layer over the poster so detach
 * transitions reveal the poster image, not a black flash.
 *
 * [altText] is the author-provided video description (parity with the
 * feed's PostCardVideoEmbed contentDescription). Forwarded to the
 * poster image's `contentDescription` so TalkBack announces the same
 * text when the screen first enters Ready before the surface attaches.
 */
@Immutable
internal data class VideoPlayerState(
    val loadStatus: VideoPlayerLoadStatus = VideoPlayerLoadStatus.Idle,
    val posterUrl: String? = null,
    val altText: String? = null,
    val isMuted: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val chromeVisible: Boolean = true,
) : UiState

/**
 * Mutually-exclusive load lifecycle for the fullscreen surface.
 *
 * - [Idle]: VM constructed but no resolution attempted yet (rarely
 *   observed; the VM immediately moves to Resolving on init).
 * - [Resolving]: resolving the post URI to a playlist URL (cached
 *   EmbedUi.Video preferred, getPosts fallback) and binding the holder.
 * - [Ready]: holder is bound to the right URL + setMode(Fullscreen)
 *   succeeded. PlayerSurface renders against `holder.player`.
 * - [Error]: resolution failed OR playback error surfaced via the
 *   holder's playbackError flow. The screen shows a centered error
 *   layout with a Retry button.
 */
internal sealed interface VideoPlayerLoadStatus {
    @Immutable data object Idle : VideoPlayerLoadStatus

    @Immutable data object Resolving : VideoPlayerLoadStatus

    @Immutable data object Ready : VideoPlayerLoadStatus

    @Immutable
    data class Error(
        val error: VideoPlayerError,
    ) : VideoPlayerLoadStatus
}

internal sealed interface VideoPlayerEvent : UiEvent {
    data object PlayPauseClicked : VideoPlayerEvent

    data object MuteClicked : VideoPlayerEvent

    data class SeekTo(
        val positionMs: Long,
    ) : VideoPlayerEvent

    data object ToggleChrome : VideoPlayerEvent

    data object BackClicked : VideoPlayerEvent

    data object RetryClicked : VideoPlayerEvent
}

internal sealed interface VideoPlayerEffect : UiEffect {
    /** Pop the fullscreen route off the back stack. */
    data object NavigateBack : VideoPlayerEffect
}
