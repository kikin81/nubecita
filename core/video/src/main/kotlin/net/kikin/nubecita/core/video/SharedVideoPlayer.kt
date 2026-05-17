@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/**
 * Process-scoped owner of *the* `ExoPlayer` instance.
 *
 * Multiple Compose surfaces (feed cards, fullscreen route) attach and
 * detach against this holder; the underlying player is never recreated
 * across navigation transitions. Audio-focus discipline and unmute
 * state live here (not on the surface) so flipping between
 * [PlaybackMode.FeedPreview] and [PlaybackMode.Fullscreen] is a mode
 * change, not a re-prepare.
 *
 * Constructor takes the [ExoPlayer] + [DefaultTrackSelector] so unit
 * tests can inject relaxed mockks. Production code uses
 * [createSharedVideoPlayer] to wire the real Media3 chain.
 *
 * Design: `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`.
 */
class SharedVideoPlayer
    internal constructor(
        private val player: ExoPlayer,
        private val trackSelector: DefaultTrackSelector,
        private val scope: CoroutineScope,
        private val idleReleaseMs: Long,
    ) {
        private val _mode = MutableStateFlow(PlaybackMode.FeedPreview)
        val mode: StateFlow<PlaybackMode> = _mode.asStateFlow()

        private val _boundPlaylistUrl = MutableStateFlow<String?>(null)
        val boundPlaylistUrl: StateFlow<String?> = _boundPlaylistUrl.asStateFlow()

        private val _isPlaying = MutableStateFlow(false)
        val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        // positionMs / durationMs are declared on the contract here in zak.1
        // so consumers can compile against the final shape. The polling
        // Job that actually updates them lives in zak.4 (VM-level concern
        // — only matters when a fullscreen surface needs the seek bar to
        // reflect playback progress).
        private val _positionMs = MutableStateFlow(0L)
        val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

        private val _durationMs = MutableStateFlow(0L)
        val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

        @Suppress("unused") // Reserved for future mutations + idle timer.
        private val mutationMutex = Mutex()

        /**
         * Bind the holder to a video. Idempotent on same `playlistUrl`:
         * a re-bind to the URL already in [boundPlaylistUrl] is a no-op,
         * which is the load-bearing property for the feed → fullscreen
         * instance-transfer. Different URL triggers `setMediaItem` +
         * `prepare`; the previous media item is replaced.
         *
         * [posterUrl] is reserved for a future poster-binding seam — the
         * surface composables resolve their own poster image today, so
         * this method only stores the URL but doesn't act on it. Kept on
         * the contract so future bind-time poster fetches don't require
         * a breaking API change.
         */
        fun bind(
            playlistUrl: String,
            posterUrl: String?,
        ) {
            if (_boundPlaylistUrl.value == playlistUrl) return
            player.setMediaItem(
                androidx.media3.common.MediaItem
                    .fromUri(playlistUrl),
            )
            player.prepare()
            _boundPlaylistUrl.value = playlistUrl
        }
    }
