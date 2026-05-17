package net.kikin.nubecita.core.video

import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.mockk.mockk
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
