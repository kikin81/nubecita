package net.kikin.nubecita.core.video.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VerticalVideoPlaylistPlayerTest {
    private val created = mutableListOf<ExoPlayer>()

    private fun newPlayer(): ExoPlayer {
        val p = mockk<ExoPlayer>(relaxed = true)
        every { p.isPlaying } returns false
        created += p
        return p
    }

    private fun pool() =
        VerticalVideoPlaylistPlayer(
            playerProvider = { newPlayer() },
            mainDispatcher = UnconfinedTestDispatcher(),
            buildMediaItem = { mockk<MediaItem>(relaxed = true) },
        )

    private fun sources(n: Int) = (0 until n).map { VideoSource(playlistUrl = "https://cdn.example/$it.m3u8") }

    @Test
    fun bind_createsTwoPlayers_activePlays_nextPrewarms() =
        runTest {
            val pool = pool()
            pool.bind(sources(3), startIndex = 0)

            assertEquals(2, created.size) // active + prewarm, never more
            verify { created[0].prepare() }
            verify { created[0].play() } // active
            verify { created[1].prepare() } // prewarm prepared...
            verify(exactly = 0) { created[1].play() } // ...but never played
            verify { created[1].pause() }
            assertSame(created[0], pool.activePlayer.value)
        }

    @Test
    fun swipeForward_promotesPrewarmedPlayer_withoutRebinding_andCreatesNoNewPlayer() =
        runTest {
            val pool = pool()
            pool.bind(sources(3), startIndex = 0)
            // created[1] is prewarmed for index 1; it was setMediaItem'd exactly once (at bind).
            pool.onActiveIndexChanged(1)

            assertEquals(2, created.size) // pool cap holds — no 3rd player
            verify { created[1].play() } // the prewarmed player is promoted...
            verify(exactly = 1) { created[1].setMediaItem(any()) } // ...without a re-prepare (fast path)
            verify(atLeast = 2) { created[0].setMediaItem(any()) } // old active recycled to prewarm index 2
            assertSame(created[1], pool.activePlayer.value)
        }

    @Test
    fun lastItem_skipsPrewarm_usesSinglePlayer() =
        runTest {
            val pool = pool()
            pool.bind(sources(1), startIndex = 0)

            assertEquals(1, created.size)
            verify { created[0].play() }
        }

    @Test
    fun onlyOnePlaybackIsActive() =
        runTest {
            val pool = pool()
            pool.bind(sources(3), startIndex = 0)

            verify { created[0].play() }
            verify(exactly = 0) { created[1].play() }
            verify { created[1].pause() }
        }

    @Test
    fun setMuted_setsActivePlayerVolume() =
        runTest {
            val pool = pool()
            pool.bind(sources(3), startIndex = 0)

            pool.setMuted(true)
            verify { created[0].volume = 0f }
        }

    @Test
    fun onStop_releasesAllPlayers_thenOnStart_recreatesAndReprepares() =
        runTest {
            val pool = pool()
            pool.bind(sources(3), startIndex = 1)
            pool.onStop()

            verify { created[0].release() }
            verify { created[1].release() }
            assertNull(pool.activePlayer.value)

            pool.onStart()
            assertEquals(4, created.size) // two fresh players
            verify { created[2].prepare() }
            verify { created[2].play() }
        }

    @Test
    fun bindEmpty_isIdle_noPlayers() =
        runTest {
            val pool = pool()
            pool.bind(emptyList(), startIndex = 0)

            assertEquals(0, created.size)
            assertNull(pool.activePlayer.value)
        }
}
