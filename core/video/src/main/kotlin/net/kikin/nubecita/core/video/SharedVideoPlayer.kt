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
 * detach against this holder; the underlying player is lazily created
 * on the first [bind] call and recreated after [release] or idle-release.
 * Audio-focus discipline and unmute state live here (not on the surface)
 * so flipping between [PlaybackMode.FeedPreview] and
 * [PlaybackMode.Fullscreen] is a mode change, not a re-prepare.
 *
 * Constructor takes a [playerFactory] lambda so unit tests can inject
 * relaxed mockks. Production code uses [createSharedVideoPlayer] to wire
 * the real Media3 chain.
 *
 * Design: `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`.
 */
class SharedVideoPlayer
    internal constructor(
        private val playerFactory: () -> ExoPlayer,
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

        // Populated by zak.4's Player.Listener wiring. Surfaces ExoPlayer
        // playback errors (ExoPlaybackException, HLS playlist 404, codec
        // failures) so the VideoPlayerViewModel can map them to a
        // VideoPlayerLoadStatus.Error variant.
        private val _playbackError = MutableStateFlow<Throwable?>(null)
        val playbackError: StateFlow<Throwable?> = _playbackError.asStateFlow()

        // Not yet wired — all zak.1 mutations happen on the main thread
        // (single-threaded tests). Mutex enforcement + concurrency tests
        // land in zak.2 when the feed coordinator drives this holder
        // concurrently with the VideoPlayerViewModel.
        @Suppress("unused")
        private val mutationMutex = Mutex()

        // Raw ExoPlayer reference — no underscore since it has no public counterpart;
        // the public observable is `player: StateFlow<Player?>` backed by `_player`.
        private var cachedExoPlayer: ExoPlayer? = null
        private val _player = MutableStateFlow<androidx.media3.common.Player?>(null)

        /**
         * Reactive view of the currently-bound ExoPlayer instance. Emits
         * `null` until the first surface attaches and `bind()` triggers
         * lazy construction, then the live ExoPlayer; emits `null`
         * again when `release()` or the idle-release timer tears the
         * player down. Compose surfaces render `PlayerSurface(player =
         * player.collectAsStateWithLifecycle().value)` against this so
         * recomposition tracks the bound state automatically; the
         * static poster image underneath the surface (per the design's
         * "surface composition rule") shows through whenever the value
         * is null.
         */
        val player: StateFlow<androidx.media3.common.Player?> = _player.asStateFlow()

        private fun requirePlayer(): ExoPlayer =
            cachedExoPlayer ?: playerFactory().also {
                cachedExoPlayer = it
                _player.value = it
            }

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
                        cachedExoPlayer?.release()
                        cachedExoPlayer = null
                        _player.value = null
                        _mode.value = PlaybackMode.FeedPreview
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
            val p = requirePlayer()
            when (target) {
                PlaybackMode.Fullscreen -> {
                    p.setAudioAttributes(attrs, true)
                    p.volume = 1f
                }
                PlaybackMode.FeedPreview -> {
                    p.setAudioAttributes(attrs, false)
                    p.volume = 0f
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
            requirePlayer().play()
            _isPlaying.value = true
        }

        /** Pause playback. The bound URL stays — re-binding to the same URL is idempotent. */
        fun pause() {
            requirePlayer().pause()
            _isPlaying.value = false
        }

        /** Seek within the current media item. */
        fun seekTo(positionMs: Long) {
            requirePlayer().seekTo(positionMs)
        }

        /**
         * Flip volume between 0f and 1f when in [PlaybackMode.Fullscreen].
         * No-op in [PlaybackMode.FeedPreview]: the silent-preview
         * contract pins volume at 0, and unmute requires entering
         * Fullscreen first (which itself flips volume to 1). Calling
         * this in FeedPreview would otherwise silently break the
         * "never interrupt the user's music" invariant.
         */
        fun toggleMute() {
            if (_mode.value != PlaybackMode.Fullscreen) return
            val p = requirePlayer()
            p.volume = if (p.volume > 0f) 0f else 1f
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
            cachedExoPlayer?.release()
            cachedExoPlayer = null
            _player.value = null
            _mode.value = PlaybackMode.FeedPreview
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
         * If the player was previously released (manual or idle-release),
         * the factory is invoked to construct a fresh [ExoPlayer] before
         * proceeding — this is the lazy-reconstruction contract.
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
            val p = requirePlayer()
            p.setMediaItem(
                androidx.media3.common.MediaItem
                    .fromUri(playlistUrl),
            )
            p.prepare()
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
 *
 * The [ExoPlayer] is constructed lazily inside the [playerFactory] lambda
 * so the holder can recreate it after [SharedVideoPlayer.release] without
 * needing a new [SharedVideoPlayer] instance.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun createSharedVideoPlayer(
    context: android.content.Context,
    scope: CoroutineScope,
    idleReleaseMs: Long = DEFAULT_IDLE_RELEASE_MS,
): SharedVideoPlayer {
    val appContext = context.applicationContext
    return SharedVideoPlayer(
        playerFactory = {
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
            ExoPlayer
                .Builder(appContext)
                .setTrackSelector(trackSelector)
                .build()
                .apply {
                    volume = 0f
                    // FeedPreview default — flipped to `true` by setMode(Fullscreen).
                    setAudioAttributes(attrs, false)
                }
        },
        scope = scope,
        idleReleaseMs = idleReleaseMs,
    )
}

/** 30 seconds. Calibrated to the design's idle-release rule. */
const val DEFAULT_IDLE_RELEASE_MS: Long = 30_000L
