@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

        private var refcount: Int = 0
        private var idleReleaseJob: Job? = null

        /**
         * Increment the active-surface refcount. The first call after a
         * zero-refcount state cancels any pending idle-release timer so
         * the ExoPlayer instance survives a quick detach → attach
         * round-trip (which happens during the feed → fullscreen
         * surface hand-off as one PlayerSurface unmounts and another
         * mounts).
         */
        fun attachSurface() {
            refcount += 1
            idleReleaseJob?.cancel()
            idleReleaseJob = null
        }

        /**
         * Decrement the refcount. Clamps at zero so a stray detach from
         * a never-attached composable can't poison the count. When the
         * count drops to zero, schedules an idle-release job that calls
         * [ExoPlayer.release] after [idleReleaseMs] of continuous
         * zero-refcount. Hardware video decoders are finite; pinning
         * the player while the user reads text posts is wasteful.
         */
        fun detachSurface() {
            if (refcount == 0) return
            refcount -= 1
            if (refcount == 0) {
                idleReleaseJob?.cancel()
                idleReleaseJob =
                    scope.launch {
                        delay(idleReleaseMs)
                        player.release()
                        _boundPlaylistUrl.value = null
                        _isPlaying.value = false
                    }
            }
        }

        /**
         * Flip the holder's [PlaybackMode]. Idempotent on same mode.
         *
         * On [PlaybackMode.Fullscreen]: ExoPlayer's audio attributes get
         * `handleAudioFocus = true`, so Media3's built-in handler claims
         * focus on the next `play()` and pauses on transient loss
         * (incoming call, other media). Volume goes to 1.
         *
         * On [PlaybackMode.FeedPreview]: `handleAudioFocus = false`
         * (silent preview must never interrupt the user's music) and
         * volume = 0.
         */
        fun setMode(target: PlaybackMode) {
            if (_mode.value == target) return
            val attrs = audioAttributes
            when (target) {
                PlaybackMode.Fullscreen -> {
                    player.setAudioAttributes(attrs, true)
                    player.volume = 1f
                }
                PlaybackMode.FeedPreview -> {
                    player.setAudioAttributes(attrs, false)
                    player.volume = 0f
                }
            }
            _mode.value = target
        }

        private val audioAttributes: androidx.media3.common.AudioAttributes =
            androidx.media3.common.AudioAttributes
                .Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

        /** Resume playback. Volume + audio-focus state come from the current [mode]. */
        fun play() {
            player.play()
            _isPlaying.value = true
        }

        /** Pause playback. The bound URL stays — re-binding to the same URL is idempotent. */
        fun pause() {
            player.pause()
            _isPlaying.value = false
        }

        /** Seek within the current media item. */
        fun seekTo(positionMs: Long) {
            player.seekTo(positionMs)
        }

        /**
         * Flip volume between 0f and 1f. Only meaningful in
         * [PlaybackMode.Fullscreen] — in [PlaybackMode.FeedPreview] the
         * mode contract pins volume at 0, and unmute requires entering
         * Fullscreen first.
         */
        fun toggleMute() {
            player.volume = if (player.volume > 0f) 0f else 1f
        }

        /**
         * Force-release the underlying ExoPlayer immediately and clear
         * the bound URL. Used by the auth-state-cleared broadcaster on
         * logout so a stale player doesn't survive across users. Also
         * called by the idle-release timer, which goes through the same
         * cleanup path.
         */
        fun release() {
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            player.release()
            _boundPlaylistUrl.value = null
            _isPlaying.value = false
        }

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

/**
 * Production factory for [SharedVideoPlayer]. Wires the real Media3
 * chain: an `ExoPlayer` built with a `DefaultTrackSelector` whose
 * HLS-bitrate floor starts pinned to the lowest variant (the
 * sustained-playback unlock arrives in a follow-up task for
 * `nubecita-zak.4`; the floor stays unconditionally pinned for
 * `zak.1`). Audio attributes start at `FeedPreview` defaults
 * (`handleAudioFocus = false`, volume = 0) — `setMode(Fullscreen)`
 * flips them in.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createSharedVideoPlayer(
    context: android.content.Context,
    scope: CoroutineScope,
    idleReleaseMs: Long = DEFAULT_IDLE_RELEASE_MS,
): SharedVideoPlayer {
    val appContext = context.applicationContext
    val trackSelector =
        DefaultTrackSelector(appContext).apply {
            setParameters(buildUponParameters().setForceLowestBitrate(true))
        }
    val attrs =
        androidx.media3.common.AudioAttributes
            .Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
    val player =
        ExoPlayer
            .Builder(appContext)
            .setTrackSelector(trackSelector)
            .build()
            .apply {
                volume = 0f
                // FeedPreview default — flipped to `true` by setMode(Fullscreen).
                setAudioAttributes(attrs, false)
            }
    return SharedVideoPlayer(
        player = player,
        trackSelector = trackSelector,
        scope = scope,
        idleReleaseMs = idleReleaseMs,
    )
}

/** 30 seconds. Calibrated to the design's idle-release rule. */
const val DEFAULT_IDLE_RELEASE_MS: Long = 30_000L
