package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SharedVideoPlayer]. The class takes a [playerFactory]
 * lambda via its constructor so tests inject relaxed mockks; the
 * production factory `createSharedVideoPlayer(...)` wires the real
 * Media3 chain inside the lambda.
 *
 * The harness uses `TestScope(UnconfinedTestDispatcher())` as the
 * holder's internal coroutine scope so the idle-release timer and
 * mutex-serialized mutations can be driven deterministically via
 * `advanceTimeBy`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SharedVideoPlayerTest {
    @Test
    fun initialState_isFeedPreviewWithNoBoundUrl() =
        runTest {
            val (holder, _) = newHolder(testScope = this)

            assertEquals(PlaybackMode.FeedPreview, holder.mode.value)
            assertNull(holder.boundPlaylistUrl.value)
            assertEquals(false, holder.isPlaying.value)
        }

    @Test
    fun bind_firstCall_setsMediaItemAndPrepares() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.bind(playlistUrl = "https://video.cdn/hls/abc.m3u8", posterUrl = null)

            verify(exactly = 1) { player.setMediaItem(any<androidx.media3.common.MediaItem>()) }
            verify(exactly = 1) { player.prepare() }
            assertEquals("https://video.cdn/hls/abc.m3u8", holder.boundPlaylistUrl.value)
        }

    @Test
    fun bind_sameUrlTwice_isIdempotent_noRebind() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.bind("https://video.cdn/hls/abc.m3u8", null)
            holder.bind("https://video.cdn/hls/abc.m3u8", null)

            // Pin the load-bearing property: re-bind on the SAME URL is a no-op,
            // which is how the feed → fullscreen instance-transfer payoff works.
            // VideoPlayerScreen.LaunchedEffect calls bind() with the post's URL
            // unconditionally; if the holder is already on that URL, no prepare
            // cycle happens and playback continues uninterrupted.
            verify(exactly = 1) { player.setMediaItem(any<androidx.media3.common.MediaItem>()) }
            verify(exactly = 1) { player.prepare() }
        }

    @Test
    fun bind_differentUrl_rebinds() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            holder.bind("https://video.cdn/hls/b.m3u8", null)

            verify(exactly = 2) { player.setMediaItem(any<androidx.media3.common.MediaItem>()) }
            verify(exactly = 2) { player.prepare() }
            assertEquals("https://video.cdn/hls/b.m3u8", holder.boundPlaylistUrl.value)
        }

    @Test
    fun setMode_fullscreen_setsAudioAttributesWithHandleAudioFocusTrue_andUnmutes() =
        runTest {
            val (holder, player) = newHolder(testScope = this)

            holder.setMode(PlaybackMode.Fullscreen)

            io.mockk.verify {
                player.setAudioAttributes(any<androidx.media3.common.AudioAttributes>(), eq(true))
                player.volume = 1f
            }
            assertEquals(PlaybackMode.Fullscreen, holder.mode.value)
        }

    @Test
    fun setMode_feedPreview_setsAudioAttributesWithHandleAudioFocusFalse_andMutes() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.setMode(PlaybackMode.Fullscreen) // start in Fullscreen so the flip is observable
            io.mockk.clearMocks(player, answers = false)

            holder.setMode(PlaybackMode.FeedPreview)

            io.mockk.verify {
                player.setAudioAttributes(any<androidx.media3.common.AudioAttributes>(), eq(false))
                player.volume = 0f
            }
            assertEquals(PlaybackMode.FeedPreview, holder.mode.value)
        }

    @Test
    fun setMode_sameMode_isNoOp() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.setMode(PlaybackMode.Fullscreen)
            io.mockk.clearMocks(player, answers = false)

            holder.setMode(PlaybackMode.Fullscreen)

            // No second flip — already in Fullscreen.
            io.mockk.verify(exactly = 0) {
                player.setAudioAttributes(any<androidx.media3.common.AudioAttributes>(), any())
            }
        }

    @Test
    fun setMode_fullscreen_liftsHlsBitrateFloor() =
        runTest {
            val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
            val initialParams =
                mockk<androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters>(relaxed = true)
            val unlockedParams =
                mockk<androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters>(relaxed = true)
            val builder =
                mockk<androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder>(relaxed = true)
            every { trackSelector.buildUponParameters() } returns builder
            every { builder.setForceLowestBitrate(false) } returns builder
            every { builder.setForceLowestBitrate(true) } returns builder
            every { builder.build() } returns unlockedParams
            val (holder, _) = newHolderWith(testScope = this, trackSelector = trackSelector)

            holder.setMode(PlaybackMode.Fullscreen)

            io.mockk.verify { builder.setForceLowestBitrate(false) }
            io.mockk.verify { trackSelector.setParameters(builder) }
        }

    @Test
    fun setMode_feedPreview_rePinsHlsBitrateFloor() =
        runTest {
            val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
            val builder =
                mockk<androidx.media3.exoplayer.trackselection.DefaultTrackSelector.Parameters.Builder>(relaxed = true)
            every { trackSelector.buildUponParameters() } returns builder
            every { builder.setForceLowestBitrate(any()) } returns builder
            val (holder, _) = newHolderWith(testScope = this, trackSelector = trackSelector)

            // Start in Fullscreen so the flip back to FeedPreview is
            // observable; clear the Fullscreen-entry calls so the
            // re-pin verification isn't drowned in noise.
            holder.setMode(PlaybackMode.Fullscreen)
            io.mockk.clearMocks(trackSelector, builder, answers = false)
            every { trackSelector.buildUponParameters() } returns builder
            every { builder.setForceLowestBitrate(any()) } returns builder

            holder.setMode(PlaybackMode.FeedPreview)

            io.mockk.verify { builder.setForceLowestBitrate(true) }
            io.mockk.verify { trackSelector.setParameters(builder) }
        }

    @Test
    fun attachSurface_then_detachAllSurfaces_within_idleWindow_doesNotRelease() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            holder.attachSurface()
            holder.detachSurface()
            // Within the 30-second idle window — re-attaching cancels the timer.
            advanceTimeBy(15_000L)
            holder.attachSurface()
            advanceTimeBy(60_000L)

            io.mockk.verify(exactly = 0) { player.release() }
        }

    @Test
    fun attachSurface_detached_idleTimeoutElapses_callsRelease_andClearsBoundUrl() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            holder.attachSurface()
            holder.detachSurface()
            advanceTimeBy(30_000L)
            runCurrent()

            io.mockk.verify(exactly = 1) { player.release() }
            assertNull(holder.boundPlaylistUrl.value, "bound URL should clear when ExoPlayer releases")
        }

    @Test
    fun detachSurface_whenAlreadyAtZero_isNoOp_andLaterMatchedDetachStillReleases() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            // Stray detaches with no attach — refcount must clamp at zero,
            // not go negative. Otherwise a stray onDispose in a never-
            // attached composable would poison the count and prevent
            // later idle-release timers from firing.
            holder.detachSurface()
            holder.detachSurface()
            holder.attachSurface()
            holder.detachSurface()

            // Refcount is back at zero; the timer should fire normally.
            advanceTimeBy(30_000L)
            runCurrent()

            // Verifies the stray detaches didn't poison the refcount —
            // the matched attach/detach pair still drives the timer to
            // exactly one release call.
            io.mockk.verify(exactly = 1) { player.release() }
        }

    @Test
    fun play_callsPlayerPlay() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.play()
            io.mockk.verify { player.play() }
        }

    @Test
    fun pause_callsPlayerPause() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.pause()
            io.mockk.verify { player.pause() }
        }

    @Test
    fun seekTo_callsPlayerSeekTo_withPositionMs() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.seekTo(12_345L)
            io.mockk.verify { player.seekTo(12_345L) }
        }

    @Test
    fun toggleMute_inFullscreen_flipsVolumeBetweenZeroAndOne() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.setMode(PlaybackMode.Fullscreen)
            io.mockk.clearMocks(player, answers = false)
            io.mockk.every { player.volume } returns 1f

            holder.toggleMute()

            io.mockk.verify { player.volume = 0f }
        }

    @Test
    fun release_callsPlayerRelease_andClearsBoundUrl() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            holder.bind("https://video.cdn/hls/a.m3u8", null)

            holder.release()

            io.mockk.verify { player.release() }
            assertNull(holder.boundPlaylistUrl.value)
        }

    @Test
    fun bind_afterRelease_recreatesPlayer_andSetsMediaItem() =
        runTest {
            val player = mockk<ExoPlayer>(relaxed = true)
            val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
            var invocations = 0
            val testDispatcher =
                coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!
            val holder =
                SharedVideoPlayer(
                    playerFactory = { _ ->
                        invocations += 1
                        player
                    },
                    trackSelectorFactory = { trackSelector },
                    scope = this,
                    mainDispatcher = testDispatcher,
                    idleReleaseMs = 30_000L,
                )

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            assertEquals(1, invocations, "first bind should create the player")

            holder.release()
            assertNull(holder.boundPlaylistUrl.value)
            assertEquals(PlaybackMode.FeedPreview, holder.mode.value)

            holder.bind("https://video.cdn/hls/b.m3u8", null)
            assertEquals(2, invocations, "bind after release must invoke the factory again")
            assertEquals("https://video.cdn/hls/b.m3u8", holder.boundPlaylistUrl.value)
        }

    @Test
    fun toggleMute_inFeedPreview_isNoOp_volumeUnchanged() =
        runTest {
            // FeedPreview's silent contract: toggleMute MUST NOT flip
            // volume to 1, even if the caller invokes it accidentally.
            val (holder, player) = newHolder(testScope = this)

            // Holder starts in FeedPreview. Call toggleMute without
            // setMode(Fullscreen) first.
            holder.toggleMute()

            // The volume setter must NEVER have been invoked.
            io.mockk.verify(exactly = 0) { player.volume = any() }
        }

    @Test
    fun player_emitsBoundInstanceOnBind_andClearsOnRelease() =
        runTest {
            val (holder, mockPlayer) = newHolder(testScope = this)

            // Before bind: emits null (no factory invocation yet).
            assertNull(holder.player.value)

            holder.bind("https://video.cdn/hls/a.m3u8", null)

            // After bind: emits the lazily-constructed player instance.
            assertEquals(mockPlayer, holder.player.value)

            holder.release()

            // After release: emits null again. The next bind would
            // re-invoke the factory; verified separately by
            // bind_afterRelease_recreatesPlayer_andSetsMediaItem.
            assertNull(holder.player.value)
        }

    @Test
    fun listener_onIsPlayingChanged_updatesIsPlayingFlow() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            // Capture the listener that the holder attaches to the player.
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)

            // Lazy player construction registers the listener.
            verify(atLeast = 1) { player.addListener(any<androidx.media3.common.Player.Listener>()) }

            // Simulate the player firing onIsPlayingChanged.
            listenerSlot.captured.onIsPlayingChanged(true)
            assertEquals(true, holder.isPlaying.value)

            listenerSlot.captured.onIsPlayingChanged(false)
            assertEquals(false, holder.isPlaying.value)
        }

    @Test
    fun listener_onPlayerError_updatesPlaybackErrorFlow() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            assertNull(holder.playbackError.value)

            val exoError = mockk<androidx.media3.common.PlaybackException>(relaxed = true)
            listenerSlot.captured.onPlayerError(exoError)
            assertEquals(exoError, holder.playbackError.value)
        }

    @Test
    fun listener_onPlaybackStateReady_updatesDurationFlow() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            every { player.duration } returns 42_500L
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            assertEquals(0L, holder.durationMs.value)

            listenerSlot.captured.onPlaybackStateChanged(androidx.media3.common.Player.STATE_READY)
            assertEquals(42_500L, holder.durationMs.value)
        }

    @Test
    fun positionPolling_runsWhilePlaying_updatesPositionMsEvery250ms() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit
            every { player.currentPosition } returnsMany listOf(0L, 250L, 500L, 750L)

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            listenerSlot.captured.onIsPlayingChanged(true)
            runCurrent()
            assertEquals(0L, holder.positionMs.value)

            advanceTimeBy(250L)
            runCurrent()
            assertEquals(250L, holder.positionMs.value)

            advanceTimeBy(500L)
            runCurrent()
            assertEquals(750L, holder.positionMs.value)

            // Cancel the polling job so runTest doesn't hang waiting for an
            // infinite loop to complete.
            listenerSlot.captured.onIsPlayingChanged(false)
        }

    @Test
    fun positionPolling_stopsOnIsPlayingFalse() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit
            every { player.currentPosition } returnsMany listOf(0L, 250L, 999L, 999L)

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            listenerSlot.captured.onIsPlayingChanged(true)
            advanceTimeBy(250L)
            runCurrent()
            assertEquals(250L, holder.positionMs.value)

            // Stop playing: the polling job should be cancelled, position
            // stays at last-known value.
            listenerSlot.captured.onIsPlayingChanged(false)
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(250L, holder.positionMs.value, "position must not advance after pause")
        }

    @Test
    fun attachSurface_zeroToOneTransition_restartsPollingIfPlayerAlreadyPlaying() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit
            every { player.currentPosition } returnsMany listOf(0L, 250L, 500L, 750L, 1_000L, 1_250L)

            // First attach + start playback drives polling.
            holder.bind("https://video.cdn/hls/a.m3u8", null)
            holder.attachSurface()
            listenerSlot.captured.onIsPlayingChanged(true)
            advanceTimeBy(250L)
            runCurrent()
            assertEquals(250L, holder.positionMs.value)

            // Detach + immediate re-attach while player is still playing —
            // no isPlaying state change occurs, so without an explicit
            // restart positionMs would freeze for the new surface.
            every { player.isPlaying } returns true
            holder.detachSurface()
            holder.attachSurface()
            advanceTimeBy(500L)
            runCurrent()
            assertEquals(
                1_000L,
                holder.positionMs.value,
                "polling must resume on the 0→1 attach when the player is still playing",
            )

            // Stop polling so runTest doesn't hang waiting for the infinite
            // delay-loop to terminate.
            listenerSlot.captured.onIsPlayingChanged(false)
        }

    @Test
    fun detachSurface_atRefcountZero_stopsPositionPollingImmediately() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit
            every { player.currentPosition } returnsMany listOf(0L, 250L, 500L, 999L, 999L)

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            holder.attachSurface()
            listenerSlot.captured.onIsPlayingChanged(true)
            advanceTimeBy(250L)
            runCurrent()
            assertEquals(250L, holder.positionMs.value)

            // Refcount drops to zero. Polling must stop NOW, not after the
            // idle-release grace window — no surface is observing positionMs
            // anymore.
            holder.detachSurface()
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(250L, holder.positionMs.value, "position must freeze the instant refcount hits 0")
        }

    @Test
    fun listener_onVideoSizeChanged_publishesAspectRatio_withPixelRatioApplied() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            assertNull(holder.videoAspectRatio.value)

            // Square-pixel 1920x1080 → ~16:9.
            listenerSlot.captured.onVideoSizeChanged(
                androidx.media3.common.VideoSize(1920, 1080, 1.0f),
            )
            assertEquals(1920f / 1080f, holder.videoAspectRatio.value)

            // Anamorphic 1440x1080 with par=4/3 → display 1920x1080 ≈ 16:9.
            // (Some legacy HLS streams encode 4:3 storage with anamorphic
            // par for 16:9 display — multiplying par in keeps the
            // rendered Box matching display aspect.)
            listenerSlot.captured.onVideoSizeChanged(
                androidx.media3.common.VideoSize(1440, 1080, 4f / 3f),
            )
            assertEquals(1440f * (4f / 3f) / 1080f, holder.videoAspectRatio.value)
        }

    @Test
    fun listener_onVideoSizeChanged_ignoresZeroDimensions() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            // Media3 emits VideoSize.UNKNOWN (0, 0) when no frame has
            // been decoded; the consumer must not divide by zero.
            listenerSlot.captured.onVideoSizeChanged(
                androidx.media3.common.VideoSize(0, 0, 1.0f),
            )
            assertNull(holder.videoAspectRatio.value)
        }

    @Test
    fun bind_newUrl_resetsVideoAspectRatio() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            listenerSlot.captured.onVideoSizeChanged(
                androidx.media3.common.VideoSize(1920, 1080, 1.0f),
            )
            assertEquals(1920f / 1080f, holder.videoAspectRatio.value)

            // Binding to a different URL clears the cached dimensions —
            // the consumer should fall back to the lexicon hint for the
            // new clip until the next onVideoSizeChanged fires.
            holder.bind("https://video.cdn/hls/b.m3u8", null)
            assertNull(holder.videoAspectRatio.value)
        }

    @Test
    fun listener_onPlaybackStateReady_clearsPriorPlaybackError() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit
            every { player.duration } returns 1_000L

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            val exoError = mockk<androidx.media3.common.PlaybackException>(relaxed = true)
            listenerSlot.captured.onPlayerError(exoError)
            assertEquals(exoError, holder.playbackError.value)

            // A subsequent successful re-prepare arriving as STATE_READY
            // clears the sticky error so the VM doesn't get pinned on it.
            listenerSlot.captured.onPlaybackStateChanged(androidx.media3.common.Player.STATE_READY)
            assertNull(holder.playbackError.value)
        }

    @Test
    fun bind_newUrl_clearsPriorPlaybackError() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            val exoError = mockk<androidx.media3.common.PlaybackException>(relaxed = true)
            listenerSlot.captured.onPlayerError(exoError)
            assertEquals(exoError, holder.playbackError.value)

            // Bind a different URL — the sticky error from the previous
            // media item must not leak across.
            holder.bind("https://video.cdn/hls/b.m3u8", null)
            assertNull(holder.playbackError.value)
        }

    @Test
    fun clearPlaybackError_drops_stickyError() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            val exoError = mockk<androidx.media3.common.PlaybackException>(relaxed = true)
            listenerSlot.captured.onPlayerError(exoError)
            assertEquals(exoError, holder.playbackError.value)

            holder.clearPlaybackError()
            assertNull(holder.playbackError.value)
        }

    @Test
    fun prepareCurrent_callsPlayerPrepare_andClearsError() =
        runTest {
            val (holder, player) = newHolder(testScope = this)
            val listenerSlot = slot<androidx.media3.common.Player.Listener>()
            every { player.addListener(capture(listenerSlot)) } returns Unit

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            val exoError = mockk<androidx.media3.common.PlaybackException>(relaxed = true)
            listenerSlot.captured.onPlayerError(exoError)
            clearMocks(player, answers = false, recordedCalls = true)

            holder.prepareCurrent()

            verify(exactly = 1) { player.prepare() }
            assertNull(holder.playbackError.value)
        }

    @Test
    fun prepareCurrent_isNoOpWhenPlayerReleased() =
        runTest {
            val (holder, _) = newHolder(testScope = this)
            // Player never built — cachedExoPlayer is null. Must not crash.
            holder.prepareCurrent()
            assertNull(holder.playbackError.value)
        }

    private fun newHolder(
        testScope: TestScope,
    ): Pair<SharedVideoPlayer, ExoPlayer> = newHolderWith(testScope = testScope, trackSelector = mockk(relaxed = true))

    /**
     * Variant of [newHolder] that lets the test supply a pre-stubbed
     * [DefaultTrackSelector] so it can assert on `buildUponParameters`
     * / `setParameters` calls (the bitrate-floor flips in `setMode`).
     */
    private fun newHolderWith(
        testScope: TestScope,
        trackSelector: DefaultTrackSelector,
    ): Pair<SharedVideoPlayer, ExoPlayer> {
        val player = mockk<ExoPlayer>(relaxed = true)
        // Production passes Dispatchers.Main.immediate; tests use the same
        // scheduler that drives runTest so advanceTimeBy still drives the
        // polling and idle-release jobs deterministically.
        val testDispatcher =
            testScope.coroutineContext[kotlinx.coroutines.CoroutineDispatcher]!!
        val holder =
            SharedVideoPlayer(
                playerFactory = { _ -> player },
                trackSelectorFactory = { trackSelector },
                scope = testScope,
                mainDispatcher = testDispatcher,
                idleReleaseMs = 30_000L,
            )
        return holder to player
    }
}
