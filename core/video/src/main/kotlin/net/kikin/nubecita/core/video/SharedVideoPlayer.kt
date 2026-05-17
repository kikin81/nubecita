@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
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
        private val playerFactory: (DefaultTrackSelector) -> ExoPlayer,
        private val trackSelectorFactory: () -> DefaultTrackSelector,
        private val scope: CoroutineScope,
        // All ExoPlayer touches MUST happen on the application thread —
        // `verifyApplicationThread()` throws otherwise (see
        // https://developer.android.com/guide/topics/media/issues/player-accessed-on-wrong-thread).
        // The provided [scope] is `@ApplicationScope` in production
        // (Dispatchers.Default), so background jobs that read or release
        // the player explicitly hop to this dispatcher before touching it.
        // Tests inject the same scheduler that drives `runTest` so
        // advanceTimeBy still drives the polling deterministically.
        private val mainDispatcher: CoroutineDispatcher,
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

        // Decoded video aspect ratio (`width / height`, with the codec's
        // pixelWidthHeightRatio applied for anamorphic streams). Null
        // until the first frame is rendered; once known, takes
        // precedence over the lexicon's optional aspectRatio hint —
        // covers the case where Bluesky's app.bsky.embed.video#view
        // omits aspectRatio and the consumer would otherwise fall back
        // to a hardcoded 16:9 letterbox.
        private val _videoAspectRatio = MutableStateFlow<Float?>(null)
        val videoAspectRatio: StateFlow<Float?> = _videoAspectRatio.asStateFlow()

        // Not yet wired — all zak.1 mutations happen on the main thread
        // (single-threaded tests). Mutex enforcement + concurrency tests
        // land in zak.2 when the feed coordinator drives this holder
        // concurrently with the VideoPlayerViewModel.
        @Suppress("unused")
        private val mutationMutex = Mutex()

        // Raw ExoPlayer reference — no underscore since it has no public counterpart;
        // the public observable is `player: StateFlow<Player?>` backed by `_player`.
        private var cachedExoPlayer: ExoPlayer? = null
        private var cachedTrackSelector: DefaultTrackSelector? = null
        private val _player = MutableStateFlow<androidx.media3.common.Player?>(null)

        /**
         * The currently-bound `DefaultTrackSelector`, lazy-constructed
         * alongside the ExoPlayer. Exposed for in-feed bitrate-floor
         * unlock logic (see `:feature:feed:impl`'s coordinator). zak.4's
         * VM will likely subsume this surface when the sustained-playback
         * unlock policy moves into the holder; for now the accessor is
         * internal and only `:feature:feed:impl` consumes it.
         *
         * Null after release / idle-release, populated lazily on the
         * next bind() that triggers requirePlayer().
         */
        val trackSelector: DefaultTrackSelector?
            get() = cachedTrackSelector

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

        private fun requirePlayer(): ExoPlayer {
            val existing = cachedExoPlayer
            if (existing != null) return existing
            val ts = cachedTrackSelector ?: trackSelectorFactory().also { cachedTrackSelector = it }
            val built = playerFactory(ts)
            built.addListener(playerStateListener)
            cachedExoPlayer = built
            _player.value = built
            return built
        }

        private var refcount: Int = 0
        private var idleReleaseJob: Job? = null

        private var positionPollingJob: Job? = null

        private val playerStateListener: androidx.media3.common.Player.Listener =
            object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                    if (isPlaying) startPositionPolling() else stopPositionPolling()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _playbackError.value = error
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == androidx.media3.common.Player.STATE_READY) {
                        val p = cachedExoPlayer ?: return
                        _durationMs.value = p.duration.coerceAtLeast(0L)
                        // Successful (re-)prepare clears any prior playback
                        // error so the consuming VM doesn't get stuck in an
                        // Error LoadStatus after a transient HLS failure
                        // recovers (e.g. retry → re-prepare → STATE_READY).
                        _playbackError.value = null
                    }
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    if (videoSize.width <= 0 || videoSize.height <= 0) return
                    // pixelWidthHeightRatio accounts for anamorphic streams
                    // where the storage aspect differs from the display
                    // aspect — multiply it in so the rendered Box matches
                    // the player's actual display dimensions.
                    val ratio =
                        (videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio) /
                            videoSize.height.toFloat()
                    _videoAspectRatio.value = ratio
                }
            }

        private fun startPositionPolling() {
            if (positionPollingJob?.isActive == true) return
            positionPollingJob =
                scope.launch(mainDispatcher) {
                    while (isActive) {
                        val p = cachedExoPlayer
                        if (p != null) {
                            _positionMs.value = p.currentPosition.coerceAtLeast(0L)
                        }
                        delay(POSITION_POLLING_INTERVAL_MS)
                    }
                }
        }

        private fun stopPositionPolling() {
            positionPollingJob?.cancel()
            positionPollingJob = null
        }

        /**
         * Increment the active-surface refcount. The first call after a
         * zero-refcount state cancels any pending idle-release timer so
         * the ExoPlayer instance survives a quick detach → attach
         * round-trip (which happens during the feed → fullscreen
         * surface hand-off as one PlayerSurface unmounts and another
         * mounts).
         *
         * The 0 → 1 transition also restarts position polling if the
         * player is already in `isPlaying = true`. The polling job is
         * driven by `onIsPlayingChanged`, so a refcount-zero
         * `stopPositionPolling()` leaves no listener to restart it when
         * the next surface attaches mid-playback (e.g. fullscreen route
         * → back to feed within the idle-release grace window). Without
         * this restart, `positionMs` would freeze at its last value
         * until the user paused and resumed.
         */
        fun attachSurface() {
            val wasZero = refcount == 0
            refcount += 1
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            if (wasZero && cachedExoPlayer?.isPlaying == true) {
                startPositionPolling()
            }
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
                // No surface is observing positionMs anymore — kill the
                // poller immediately to drop the 250ms background tick.
                // The idle-release timer below still tears down the player
                // itself after the grace window.
                stopPositionPolling()
                idleReleaseJob?.cancel()
                idleReleaseJob =
                    scope.launch(mainDispatcher) {
                        delay(idleReleaseMs)
                        cachedExoPlayer?.release()
                        cachedExoPlayer = null
                        cachedTrackSelector = null
                        _player.value = null
                        _isPlaying.value = false
                        _positionMs.value = 0L
                        _durationMs.value = 0L
                        _playbackError.value = null
                        _videoAspectRatio.value = null
                        _boundPlaylistUrl.value = null
                        _mode.value = PlaybackMode.FeedPreview
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
            // _isPlaying.value driven by playerStateListener.onIsPlayingChanged.
        }

        /** Pause playback. The bound URL stays — re-binding to the same URL is idempotent. */
        fun pause() {
            requirePlayer().pause()
            // _isPlaying.value driven by playerStateListener.onIsPlayingChanged.
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
            stopPositionPolling()
            idleReleaseJob?.cancel()
            idleReleaseJob = null
            cachedExoPlayer?.release()
            cachedExoPlayer = null
            cachedTrackSelector = null
            _player.value = null
            _mode.value = PlaybackMode.FeedPreview
            _boundPlaylistUrl.value = null
            _isPlaying.value = false
            _positionMs.value = 0L
            _durationMs.value = 0L
            _playbackError.value = null
            _videoAspectRatio.value = null
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
            // New media item — drop any prior playback error so the VM
            // doesn't immediately bounce back into Error before the new
            // prepare() either succeeds (STATE_READY clears) or fails
            // (onPlayerError re-sets). Also drop the cached video
            // dimensions: the next clip's aspect ratio may differ, and
            // until the new first frame is rendered the consumer should
            // fall back to the lexicon hint rather than the prior clip's
            // measured ratio.
            _playbackError.value = null
            _videoAspectRatio.value = null
            p.setMediaItem(
                androidx.media3.common.MediaItem
                    .fromUri(playlistUrl),
            )
            p.prepare()
            _boundPlaylistUrl.value = playlistUrl
        }

        /**
         * Re-prepare the currently-bound media item without changing
         * [boundPlaylistUrl]. Used by retry flows: after a transient
         * playback failure, the consumer clears the error and asks the
         * player to try the same URL again. No-op if no media item is
         * bound (i.e. the player has been released).
         */
        fun prepareCurrent() {
            val p = cachedExoPlayer ?: return
            _playbackError.value = null
            p.prepare()
        }

        /**
         * Drop a sticky [playbackError] without otherwise touching the
         * player. Used by the VM's retry path so the
         * combine(...).onEach handler doesn't bounce the screen straight
         * back into Error between the user tapping Retry and the next
         * STATE_READY (or fresh onPlayerError) arriving.
         */
        fun clearPlaybackError() {
            _playbackError.value = null
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
        playerFactory = { trackSelector ->
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
                    setAudioAttributes(attrs, false)
                }
        },
        trackSelectorFactory = {
            DefaultTrackSelector(appContext).apply {
                setParameters(buildUponParameters().setForceLowestBitrate(true))
            }
        },
        scope = scope,
        // The ExoPlayer is lazily constructed inside `playerFactory` on
        // the first `requirePlayer()` (called from bind() / setMode()),
        // and `verifyApplicationThread()` rejects any access from a
        // different thread thereafter. The internal background jobs
        // (position polling, idle-release teardown) launch on the
        // application scope (Dispatchers.Default) and hop to this
        // dispatcher before touching the player. Main.immediate keeps
        // the hop synchronous when callers are already on Main, avoiding
        // an unnecessary post-back to the looper for every player access.
        mainDispatcher = Dispatchers.Main.immediate,
        idleReleaseMs = idleReleaseMs,
    )
}

/** 30 seconds. Calibrated to the design's idle-release rule. */
const val DEFAULT_IDLE_RELEASE_MS: Long = 30_000L

private const val POSITION_POLLING_INTERVAL_MS: Long = 250L
