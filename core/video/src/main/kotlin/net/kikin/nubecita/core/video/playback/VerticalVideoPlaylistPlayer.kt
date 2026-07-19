@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package net.kikin.nubecita.core.video.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
                _playbackState.value =
                    when {
                        error != null -> PlaylistPlaybackState.Error(error)
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
        teardownPlayers()
        items = emptyList()
        activeIndex = -1
    }

    // --- internals (always on the main thread, under the mutex) ---

    private suspend fun settle(target: Int) {
        if (released) return
        val nextIndex = (target + 1).takeIf { it in items.indices }
        val neededSlots = if (nextIndex == null) 1 else 2
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
