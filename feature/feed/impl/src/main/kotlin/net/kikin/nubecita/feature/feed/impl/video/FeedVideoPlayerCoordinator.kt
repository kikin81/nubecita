@file:androidx.annotation.OptIn(UnstableApi::class)

package net.kikin.nubecita.feature.feed.impl.video

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.media3.common.AudioAttributes as Media3AudioAttributes

/**
 * Owns one `ExoPlayer` instance and binds it to the most-visible video
 * card in the feed. Phase C of the openspec change
 * `add-feature-feed-video-embeds`. Lifecycle scoped to `FeedScreen`'s
 * composition: created via `remember { FeedVideoPlayerCoordinator(...) }`
 * and released via `DisposableEffect(Unit) { onDispose { coordinator.release() } }`.
 *
 * **Single-player invariant.** At most one `ExoPlayer` is materialized
 * at any time. Re-binding to a different post is a `setMediaItem` +
 * `prepare` + `play` sequence — never `ExoPlayer.Builder(...).build()`
 * a second time. Verified at runtime via `dumpsys media.player`.
 *
 * **Autoplay-muted by default; audio focus is sacred.** The bound
 * player runs at `volume = 0` and never claims audio focus. Opening
 * the app to read the feed while listening to music does NOT
 * interrupt the user's audio. Audio focus is requested ONLY when the
 * user explicitly taps the unmute icon ([toggleMute]); released on
 * mute toggle, scroll-away from the unmuted card, focus loss, or
 * [release].
 *
 * **Race-safety.** Player-state mutations are serialized via a coroutine
 * `Mutex` so audio-focus and visibility-driven bind callbacks don't
 * interleave (e.g. a focus-loss callback firing mid-rebind).
 *
 * **HLS bitrate floor.** Initial track selection forces the lowest
 * variant via [DefaultTrackSelector]. After
 * [SUSTAINED_PLAYBACK_BITRATE_UNLOCK_MS] of continuous playback on a
 * single bound video, the force flag clears and the platform ABR
 * may upgrade. A scroll-driven rebind to a different post resets the
 * timer.
 *
 * @param context an Android `Context` retained for the broadcast
 *   receiver registration. The application context is used so the
 *   coordinator never retains the `Activity`.
 * @param audioManager the system [AudioManager]; passed in so unit
 *   tests can supply a mock.
 */
internal class FeedVideoPlayerCoordinator(
    context: Context,
    private val audioManager: AudioManager,
    /**
     * The shared `ExoPlayer`. The bound video card passes this to its
     * `PlayerSurface(player = ...)`; non-bound cards pass `null` so
     * only one surface holds the player at a time. Production
     * construction goes through [createFeedVideoPlayerCoordinator],
     * which configures the player with `volume = 0`,
     * `setHandleAudioFocus(false)` (the coordinator manages focus
     * manually), the matching track selector, and the playback-started
     * listener for the bitrate-floor unlock. Unit tests inject a
     * relaxed mock so the audio-focus contract can be exercised
     * without a real Android framework.
     */
    val player: ExoPlayer,
    private val trackSelector: DefaultTrackSelector,
) {
    private val appContext: Context = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()

    private val _boundPostId = MutableStateFlow<String?>(null)
    val boundPostId: StateFlow<String?> = _boundPostId.asStateFlow()

    private val _isUnmuted = MutableStateFlow(false)
    val isUnmuted: StateFlow<Boolean> = _isUnmuted.asStateFlow()

    private val _playbackHint = MutableStateFlow(PlaybackHint.None)
    val playbackHint: StateFlow<PlaybackHint> = _playbackHint.asStateFlow()

    /**
     * Active focus request handle; non-null while we hold focus. Kept
     * so `abandonAudioFocus` can release the same instance the
     * `requestAudioFocus` claim returned.
     */
    private var activeFocusRequest: AudioFocusRequest? = null
    private var noisyReceiverRegistered: Boolean = false
    private var bitrateUnlockJob: Job? = null
    private var released: Boolean = false

    private val systemAudioAttributes: AudioAttributes =
        AudioAttributes
            .Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()

    private val noisyReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    notifyBecomingNoisy()
                }
            }
        }

    /**
     * Hook called by [noisyReceiver] when the system delivers
     * `ACTION_AUDIO_BECOMING_NOISY` (headphones unplug, etc.).
     * Visibility-promoted to `internal` so unit tests can fire the
     * broadcast effect directly without standing up a real
     * BroadcastReceiver registration on a mocked Context.
     */
    internal fun notifyBecomingNoisy() {
        scope.launch { handleFocusLostInternal() }
    }

    private val focusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            handleSystemFocusChange(focusChange)
        }

    /**
     * Routes a system audio-focus-change event into the coordinator.
     * Visibility-promoted to `internal` so unit tests can fire focus
     * losses without reflecting into [AudioFocusRequest] (the
     * `getOnAudioFocusChangeListener()` accessor isn't exposed on
     * unit-test Android stubs). Production wiring goes through the
     * private [focusChangeListener] above.
     */
    internal fun handleSystemFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> scope.launch { handleFocusLostInternal() }
            // AUDIOFOCUS_GAIN re-emerges only when *we* already
            // requested focus and the system grants it after a
            // transient loss — Media3's internal handling is off
            // (handleAudioFocus = false), so the coordinator does
            // NOT auto-resume on gain. The user resumes via the
            // explicit "tap to resume" overlay → resume().
        }
    }

    /**
     * Bind the coordinator to a video card or unbind. Driven by
     * `FeedScreen`'s scroll-gated `LaunchedEffect` — `null` while
     * scrolling, the resting target the instant scroll settles. A
     * change of bound post id is treated as "rebind": auto-mute (if
     * the prior card was unmuted), reset the bitrate floor timer,
     * issue a fresh `setMediaItem`, and play muted.
     */
    fun bindMostVisibleVideo(target: VideoBindingTarget?) {
        scope.launch { bindInternal(target) }
    }

    /**
     * Toggle the mute / unmute state of the currently-bound card.
     * Called by the bound card's mute icon overlay. Idempotent if
     * the coordinator has no bound post (the icon should not be
     * visible in that case, but the action is a no-op for safety).
     *
     * Unmute path:
     * - Request `AUDIOFOCUS_GAIN_TRANSIENT`.
     * - If granted: register BECOMING_NOISY, set `volume = 1`,
     *   transition `isUnmuted = true`.
     * - If denied: leave state untouched.
     *
     * Mute path:
     * - Abandon focus, unregister BECOMING_NOISY, set `volume = 0`,
     *   transition `isUnmuted = false`.
     */
    fun toggleMute() {
        scope.launch { toggleMuteInternal() }
    }

    /**
     * Reacquire audio focus + resume the player after a `FocusLost`
     * interruption. Called by the bound card's "tap to resume"
     * overlay. No-op when `playbackHint != FocusLost`.
     */
    fun resume() {
        scope.launch { resumeInternal() }
    }

    /**
     * Tear down the coordinator. Called from
     * `DisposableEffect(Unit) { onDispose { ... } }` in `FeedScreen`.
     * Releases the player, abandons focus (if held), unregisters the
     * BECOMING_NOISY receiver (if registered), cancels the bitrate
     * unlock timer, and cancels the coordinator's coroutine scope so
     * no stray `bindMostVisibleVideo` from a recomposition mid-exit
     * can resurrect state. After release, all coordinator entry
     * points are no-ops.
     */
    fun release() {
        if (released) return
        // Order matters: flag → cancel scope → synchronous teardown.
        //
        // 1. `released = true` so any coroutine resuming inside the
        //    mutex (or queued to acquire it) sees the flag and exits
        //    early before mutating the player.
        // 2. `scope.cancel(...)` cancels the bitrate-unlock timer +
        //    any in-flight `bindInternal / toggleMute / resume`
        //    launches. Since the coordinator's scope is bound to
        //    `Dispatchers.Main.immediate` and every withLock body is
        //    synchronous (no suspends mid-block), no coroutine can be
        //    actively touching the player at the moment release()
        //    runs — the worst case is a coroutine suspended on
        //    `mutex.withLock` waiting to acquire, which the cancel
        //    fail-fasts.
        // 3. THEN release focus + unregister receiver + release the
        //    player. Doing this after the cancel guarantees no
        //    cancelled launch can resume and call `player.X()` on a
        //    released player.
        released = true
        bitrateUnlockJob?.cancel()
        scope.cancel("FeedVideoPlayerCoordinator released")
        if (activeFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(activeFocusRequest!!)
            activeFocusRequest = null
        }
        if (noisyReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
        player.release()
        _boundPostId.value = null
        _isUnmuted.value = false
        _playbackHint.value = PlaybackHint.None
    }

    // ---------- internal serialized handlers --------------------------------

    private suspend fun bindInternal(target: VideoBindingTarget?) =
        mutex.withLock {
            if (released) return@withLock
            val current = _boundPostId.value
            if (target?.postId == current && target != null) {
                // Already bound to this post — nothing to do. The
                // scroll-gated flow distinctUntilChanged()s on bind id,
                // but a layout recompute that doesn't change the
                // resting post still passes through.
                return@withLock
            }
            // Either rebinding to a different post OR unbinding. In both
            // cases, drop focus + auto-mute first if the prior card was
            // unmuted (per the "scroll-away from an unmuted card auto-mutes"
            // contract — Decision 5 in design.md).
            if (_isUnmuted.value) {
                releaseFocusAndUnregisterNoisy()
                player.volume = 0f
                _isUnmuted.value = false
            }
            _playbackHint.value = PlaybackHint.None
            bitrateUnlockJob?.cancel()
            bitrateUnlockJob = null
            // Reset the bitrate floor for the next playback session.
            trackSelector.setParameters(
                trackSelector.buildUponParameters().setForceLowestBitrate(true),
            )

            if (target == null) {
                player.pause()
                player.clearMediaItems()
                _boundPostId.value = null
                return@withLock
            }
            _boundPostId.value = target.postId
            player.setMediaItem(MediaItem.fromUri(target.playlistUrl))
            player.prepare()
            player.playWhenReady = true
        }

    private suspend fun toggleMuteInternal() =
        mutex.withLock {
            if (released) return@withLock
            if (_boundPostId.value == null) return@withLock
            if (_isUnmuted.value) {
                // Unmuted → muted.
                releaseFocusAndUnregisterNoisy()
                player.volume = 0f
                _isUnmuted.value = false
            } else {
                // Muted → unmuted.
                val granted = requestAudioFocus()
                if (granted) {
                    registerNoisyReceiver()
                    player.volume = 1f
                    _isUnmuted.value = true
                    _playbackHint.value = PlaybackHint.None
                }
                // Denied: silently leave muted. The user can retry.
            }
        }

    private suspend fun resumeInternal() =
        mutex.withLock {
            if (released) return@withLock
            if (_playbackHint.value != PlaybackHint.FocusLost) return@withLock
            if (_boundPostId.value == null) {
                _playbackHint.value = PlaybackHint.None
                return@withLock
            }
            val granted = requestAudioFocus()
            if (granted) {
                registerNoisyReceiver()
                player.volume = 1f
                player.play()
                _playbackHint.value = PlaybackHint.None
                // isUnmuted was already true throughout the focus loss;
                // it is preserved by design (user intent kept).
            }
            // Denied: leave the FocusLost overlay in place; user can retry.
        }

    private suspend fun handleFocusLostInternal() =
        mutex.withLock {
            if (released) return@withLock
            if (!_isUnmuted.value) return@withLock
            // Pause + mute + release focus + unregister NOISY but KEEP
            // isUnmuted = true so the user's intent survives the
            // interruption. The bound card surfaces a "tap to resume"
            // overlay driven by playbackHint = FocusLost.
            player.pause()
            player.volume = 0f
            releaseFocusAndUnregisterNoisy()
            _playbackHint.value = PlaybackHint.FocusLost
        }

    /**
     * Hook called by the player listener (wired in
     * [createFeedVideoPlayerCoordinator]) when the bound video
     * actually begins rendering. Visibility-promoted to `internal`
     * so the listener can call back here from the factory site.
     */
    internal fun notifyPlaybackStarted() {
        scope.launch { onPlaybackStarted() }
    }

    private suspend fun onPlaybackStarted() =
        mutex.withLock {
            if (released) return@withLock
            // Capture the bind id at scheduling time. The 10-second
            // delay below outlives the current player.play() call, so
            // a stale onPlaybackStarted from a previous binding could
            // otherwise unlock bitrate for a freshly-bound video. The
            // inner `mutex.withLock` re-checks both the released flag
            // and the bound post before flipping the track selector.
            val bindAtScheduling = _boundPostId.value ?: return@withLock
            if (bitrateUnlockJob?.isActive == true) return@withLock
            bitrateUnlockJob =
                scope.launch {
                    delay(SUSTAINED_PLAYBACK_BITRATE_UNLOCK_MS)
                    mutex.withLock {
                        if (released) return@withLock
                        if (_boundPostId.value != bindAtScheduling) return@withLock
                        trackSelector.setParameters(
                            trackSelector.buildUponParameters().setForceLowestBitrate(false),
                        )
                        bitrateUnlockJob = null
                    }
                }
        }

    // ---------- audio-focus helpers (always called inside mutex) ------------

    private fun requestAudioFocus(): Boolean {
        if (activeFocusRequest != null) return true
        val request =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(systemAudioAttributes)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
        val result = audioManager.requestAudioFocus(request)
        return if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            activeFocusRequest = request
            true
        } else {
            false
        }
    }

    private fun releaseFocusAndUnregisterNoisy() {
        activeFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        activeFocusRequest = null
        if (noisyReceiverRegistered) {
            runCatching { appContext.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
    }

    private fun registerNoisyReceiver() {
        if (noisyReceiverRegistered) return
        // ContextCompat handles the API 33+ explicit-export-flag
        // requirement transparently. ACTION_AUDIO_BECOMING_NOISY is a
        // system protected broadcast and the receiver is process-local,
        // so RECEIVER_NOT_EXPORTED is the correct flag.
        ContextCompat.registerReceiver(
            appContext,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        noisyReceiverRegistered = true
    }

    private fun CoroutineScope.cancel(message: String) {
        coroutineContext[Job]?.cancel(kotlinx.coroutines.CancellationException(message))
    }

    internal companion object {
        /**
         * Sustained-playback time before the HLS bitrate floor lifts.
         * Spec calls out 10 seconds (Decision 4 in design.md):
         * upgrades fire only for videos the user is genuinely
         * engaging with, not ones glanced-at-and-scrolled-past.
         */
        internal const val SUSTAINED_PLAYBACK_BITRATE_UNLOCK_MS: Long = 10_000L
    }
}

/**
 * Production factory that constructs a real `ExoPlayer` +
 * `DefaultTrackSelector` and wires the playback-started listener for
 * the bitrate-floor unlock timer. Kept out of the coordinator's
 * primary constructor so unit tests can inject relaxed mocks.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun createFeedVideoPlayerCoordinator(
    context: Context,
    audioManager: AudioManager,
): FeedVideoPlayerCoordinator {
    val appContext = context.applicationContext
    val trackSelector =
        DefaultTrackSelector(appContext).apply {
            setParameters(buildUponParameters().setForceLowestBitrate(true))
        }
    val media3AudioAttributes =
        Media3AudioAttributes
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
                // handleAudioFocus = false: Media3's built-in focus
                // handler would claim focus on every play(), which
                // contradicts the never-autoplay-claim contract. The
                // coordinator manages focus manually.
                setAudioAttributes(media3AudioAttributes, false)
            }
    val coordinator =
        FeedVideoPlayerCoordinator(
            context = appContext,
            audioManager = audioManager,
            player = player,
            trackSelector = trackSelector,
        )
    player.addListener(
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && player.playWhenReady) {
                    coordinator.notifyPlaybackStarted()
                }
            }
        },
    )
    return coordinator
}
