package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
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
            // Use a counting factory so we can verify recreation:
            val player = mockk<ExoPlayer>(relaxed = true)
            var invocations = 0
            val holder =
                SharedVideoPlayer(
                    playerFactory = {
                        invocations += 1
                        player
                    },
                    scope = this,
                    idleReleaseMs = 30_000L,
                )

            holder.bind("https://video.cdn/hls/a.m3u8", null)
            assertEquals(1, invocations, "first bind should create the player")

            holder.release()
            // After release, _player is null and mode resets to FeedPreview.
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

    private fun newHolder(
        testScope: TestScope,
    ): Pair<SharedVideoPlayer, ExoPlayer> {
        val player = mockk<ExoPlayer>(relaxed = true)
        val holder =
            SharedVideoPlayer(
                playerFactory = { player },
                scope = testScope,
                idleReleaseMs = 30_000L,
            )
        return holder to player
    }
}
