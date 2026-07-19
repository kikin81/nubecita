@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A video the playlist player can play. */
public data class VideoSource(
    val playlistUrl: String,
    val posterUrl: String? = null,
)

/** Coarse playback state of the *active* item, projected for the UI. */
public sealed interface PlaylistPlaybackState {
    public data object Idle : PlaylistPlaybackState

    public data object Buffering : PlaylistPlaybackState

    public data object Playing : PlaylistPlaybackState

    public data class Error(
        val cause: Throwable,
    ) : PlaylistPlaybackState
}

/**
 * A pooled player for a vertical video **playlist** surface (epic nubecita-zdv8).
 * Holds at most **two** ExoPlayers — the *active* one and the *next-prewarmed*
 * one — so a swipe promotes an already-`prepare()`d player and starts instantly.
 * Exactly one playback is active (playing/audible) at a time; the prewarmed
 * player is prepared, paused, and silent.
 *
 * Distinct from `SharedVideoPlayer` (which serves the heterogeneous feed) — see
 * the `video-playback-engine` design. Players are built through [playerProvider]
 * (production wires `VideoPlayerFactory`; tests inject relaxed mocks). All player
 * touches happen on [mainDispatcher] and mutations are serialized by [mutex].
 *
 * Lifecycle: [onStop] releases both players (frees decoders) when the surface
 * backgrounds; [onStart] re-prepares. Not a Hilt singleton — the hosting
 * ViewModel constructs one per vertical-feed surface and calls [release] in
 * `onCleared`.
 *
 * Decoder budget: if the active playback hits a decoder error while the pool
 * holds two players, the device can't sustain two concurrent decoders — the
 * pool frees the prewarm and retries the active as pool-of-1 for the rest of
 * the surface's life (trading instant swipes for playing at all).
 */
public class VerticalVideoPlaylistPlayer(
    private val playerProvider: suspend () -> ExoPlayer,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val buildMediaItem: (VideoSource) -> MediaItem = { MediaItem.fromUri(it.playlistUrl) },
) {
    private class Slot(
        val player: ExoPlayer,
    ) {
        /** Playlist index this player is prepared for, or null if unbound. */
        var index: Int? = null
    }

    private val mutex = Mutex()
    private val slots = mutableListOf<Slot>()

    // Own scope for self-initiated decoder-budget recovery (triggered from the
    // active player's error listener, not a caller). Cancelled in release().
    private val recoveryScope = CoroutineScope(mainDispatcher + SupervisorJob())

    // Pool ceiling. Starts at 2 (active + prewarm) and degrades to 1 the first
    // time the active playback hits a decoder error — the tell-tale of a device
    // whose concurrent hardware-decoder budget can't sustain two (emulator
    // goldfish; phones capped at 2 while a SharedVideoPlayer also holds one).
    // Once degraded, the pool runs single-player for the rest of the surface's
    // life: swipes re-prepare in place rather than promote a prewarm. (Epic
    // nubecita-zdv8 Slice 5a; spec video-playback-engine "decoder-exclusion".)
    @Volatile
    private var maxSlots: Int = 2
    private var degradedForDecoderBudget: Boolean = false

    private var items: List<VideoSource> = emptyList()
    private var activeIndex: Int = -1

    /** Remembered across [onStop]/[onStart] so a background round-trip resumes in place. */
    private var lastActiveIndex: Int = 0

    @Volatile
    private var muted: Boolean = false

    // Set by release() (which is synchronous and mutex-free, so it can run on the
    // main thread while a suspended settle() is parked at playerProvider()). settle()
    // re-checks it after each creation to abort instead of resurrecting a torn-down pool.
    @Volatile
    private var released: Boolean = false

    private var activeListenerTarget: ExoPlayer? = null

    private val _activePlayer = MutableStateFlow<Player?>(null)

    /** The ExoPlayer bound to the currently active item, for the Compose surface. */
    public val activePlayer: StateFlow<Player?> = _activePlayer.asStateFlow()

    private val _playbackState = MutableStateFlow<PlaylistPlaybackState>(PlaylistPlaybackState.Idle)
    public val playbackState: StateFlow<PlaylistPlaybackState> = _playbackState.asStateFlow()

    // Map the active player's state atomically on any relevant event, so pause /
    // STATE_ENDED transition back to Idle (an isPlaying/state-change split would
    // otherwise leave the projection stuck on Playing when the user pauses).
    private val activeListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (!events.containsAny(
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYER_ERROR,
                    )
                ) {
                    return
                }
                val error = player.playerError
                if (error != null) {
                    onActivePlayerError(error)
                    return
                }
                _playbackState.value =
                    when {
                        player.playbackState == Player.STATE_BUFFERING -> PlaylistPlaybackState.Buffering
                        player.isPlaying -> PlaylistPlaybackState.Playing
                        else -> PlaylistPlaybackState.Idle
                    }
            }
        }

    /** Attach [items] and make [startIndex] active (index+1 pre-warms). */
    public suspend fun bind(
        items: List<VideoSource>,
        startIndex: Int,
    ): Unit =
        mutex.withLock {
            withContext(mainDispatcher) {
                this@VerticalVideoPlaylistPlayer.items = items
                if (items.isEmpty()) {
                    settleToIdle()
                } else {
                    settle(startIndex.coerceIn(0, items.lastIndex))
                }
            }
        }

    /** The visible item changed to [index]; promote its prewarmed player and re-warm the next. */
    public suspend fun onActiveIndexChanged(index: Int): Unit =
        mutex.withLock {
            withContext(mainDispatcher) {
                if (index != activeIndex && index in items.indices) settle(index)
            }
        }

    /** Release both players (free decoders) when the surface backgrounds. */
    public suspend fun onStop(): Unit =
        mutex.withLock {
            withContext(mainDispatcher) { teardownPlayers() }
        }

    /** Re-prepare the active (and next) item when the surface returns to the foreground. */
    public suspend fun onStart(): Unit =
        mutex.withLock {
            withContext(mainDispatcher) {
                if (items.isNotEmpty() && slots.isEmpty()) settle(lastActiveIndex.coerceIn(0, items.lastIndex))
            }
        }

    /**
     * Mute/unmute the active playback. The prewarmed player stays silent
     * regardless. Must be called on the main thread (the UI already is).
     */
    public fun setMuted(muted: Boolean) {
        this.muted = muted
        slots.firstOrNull { it.index == activeIndex }?.player?.volume = if (muted) 0f else 1f
    }

    /** Synchronously release everything. Call from the host ViewModel's `onCleared` (main thread). */
    public fun release() {
        released = true
        recoveryScope.cancel()
        teardownPlayers()
        items = emptyList()
        activeIndex = -1
    }

    /**
     * The active player reported [error]. A decoder error on the first
     * (still pool-of-2) playback almost always means the device can't sustain
     * two concurrent decoders — free the prewarm's decoder and retry the active
     * once as pool-of-1 (spec decoder-exclusion retry) instead of surfacing a
     * hard error. Any other error (or a second failure once degraded) surfaces.
     * `internal` so the pool's error path is unit-testable without Media3's
     * `Player.Events` / `ExoPlaybackException` construction plumbing.
     */
    internal fun onActivePlayerError(error: PlaybackException) {
        if (error.isDecoderError() && !degradedForDecoderBudget && maxSlots > 1) {
            _playbackState.value = PlaylistPlaybackState.Buffering
            recoveryScope.launch { recoverFromDecoderBudget(error) }
        } else {
            _playbackState.value = PlaylistPlaybackState.Error(error)
        }
    }

    // --- internals (always on the main thread, under the mutex) ---

    private suspend fun settle(target: Int) {
        if (released) return
        // Prewarm the next item only when the pool ceiling allows a second
        // player (maxSlots drops to 1 after a decoder-budget degrade).
        val nextIndex = (target + 1).takeIf { it in items.indices && maxSlots > 1 }
        val neededSlots = if (nextIndex == null) 1 else 2
        // No shrink loop is needed here: the only place `maxSlots` drops is
        // `recoverFromDecoderBudget`, which trims `slots` to the single active
        // player and lowers `maxSlots` atomically under the same mutex — so
        // `settle` never observes `slots.size > maxSlots`.
        while (slots.size < neededSlots) {
            val player = playerProvider()
            // release() may have run while we were suspended in playerProvider() —
            // abort and release the just-built player instead of resurrecting the pool.
            if (released) {
                player.release()
                return
            }
            slots.add(Slot(player))
        }

        // Fast path: a slot already prepared for `target` (the prior prewarm) becomes
        // active with no re-prepare. Otherwise take any slot and rebind it.
        val activeSlot = slots.firstOrNull { it.index == target } ?: slots.first()
        if (activeSlot.index != target) rebind(activeSlot, target)

        if (nextIndex != null) {
            val prewarmSlot = slots.first { it !== activeSlot }
            if (prewarmSlot.index != nextIndex) rebind(prewarmSlot, nextIndex)
            prewarmSlot.player.volume = 0f
            prewarmSlot.player.pause()
        }

        // Exactly one active playback.
        slots.forEach { if (it !== activeSlot) it.player.pause() }

        activeIndex = target
        lastActiveIndex = target
        attachActiveListener(activeSlot.player)
        activeSlot.player.volume = if (muted) 0f else 1f
        activeSlot.player.play()
        _activePlayer.value = activeSlot.player
    }

    /**
     * The active playback failed to decode ([error]) while the pool held two
     * players. Degrade to a single player (freeing the prewarm's decoder) and
     * retry the active item once. If it fails again as pool-of-1, the next error
     * is a genuine playback failure and surfaces as [PlaylistPlaybackState.Error].
     */
    private suspend fun recoverFromDecoderBudget(error: PlaybackException): Unit =
        mutex.withLock {
            withContext(mainDispatcher) {
                // Torn down, or another error already drove the degrade+retry — nothing to do.
                if (released || degradedForDecoderBudget) return@withContext
                degradedForDecoderBudget = true
                maxSlots = 1

                // No active slot to retry: surface the original error instead of
                // leaving the UI stuck on the Buffering state set by onActivePlayerError.
                val active =
                    slots.firstOrNull { it.index == activeIndex }
                        ?: run {
                            _playbackState.value = PlaylistPlaybackState.Error(error)
                            return@withContext
                        }
                // Free every other player's decoder so the active has headroom.
                slots.filter { it !== active }.forEach { it.player.release() }
                slots.retainAll { it === active }

                // Re-prepare the same player (prepare() clears its error) and resume.
                rebind(active, activeIndex)
                attachActiveListener(active.player)
                active.player.volume = if (muted) 0f else 1f
                active.player.play()
                _activePlayer.value = active.player
            }
        }

    private fun rebind(
        slot: Slot,
        index: Int,
    ) {
        slot.player.setMediaItem(buildMediaItem(items[index]))
        slot.player.prepare()
        slot.index = index
    }

    private fun attachActiveListener(player: ExoPlayer) {
        if (activeListenerTarget === player) return
        activeListenerTarget?.removeListener(activeListener)
        player.addListener(activeListener)
        activeListenerTarget = player
    }

    private fun settleToIdle() {
        teardownPlayers()
        activeIndex = -1
    }

    private fun teardownPlayers() {
        activeListenerTarget?.removeListener(activeListener)
        activeListenerTarget = null
        slots.forEach { it.player.release() }
        slots.clear()
        _activePlayer.value = null
        _playbackState.value = PlaylistPlaybackState.Idle
    }
}

/**
 * The Media3 decoder-error band (init/query/decode/format failures,
 * 4001–4005) — the errors that a concurrent-decoder shortfall surfaces as.
 */
private fun PlaybackException.isDecoderError(): Boolean =
    errorCode in
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED..PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED
