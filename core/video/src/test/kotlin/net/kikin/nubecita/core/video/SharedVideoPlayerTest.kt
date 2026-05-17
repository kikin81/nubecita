package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SharedVideoPlayer]. The class takes its [ExoPlayer]
 * + [DefaultTrackSelector] via constructor so tests inject relaxed
 * mockks; the production factory `createSharedVideoPlayer(...)` wires
 * the real Media3 chain.
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

    private fun newHolder(
        testScope: TestScope,
    ): Pair<SharedVideoPlayer, ExoPlayer> {
        val player = mockk<ExoPlayer>(relaxed = true)
        val trackSelector = mockk<DefaultTrackSelector>(relaxed = true)
        val holder =
            SharedVideoPlayer(
                player = player,
                trackSelector = trackSelector,
                scope = testScope,
                idleReleaseMs = 30_000L,
            )
        return holder to player
    }
}
