@file:androidx.annotation.OptIn(UnstableApi::class)

package net.kikin.nubecita.feature.feed.impl.video

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Audio-focus contract tests for [FeedVideoPlayerCoordinator]. The
 * coordinator's player + track-selector dependencies are injected, so
 * tests use relaxed `mockk` for those and focus on the audio-focus
 * side effects on a mocked [AudioManager] + the coordinator's
 * `StateFlow` transitions.
 *
 * Spec coverage (per openspec change `add-feature-feed-video-embeds`,
 * task 4.6):
 * - (a) Autoplay flow NEVER calls `requestAudioFocus`.
 * - (b) `toggleMute()` from muted → unmuted calls `requestAudioFocus`
 *   exactly once.
 * - (c) Scroll-away from an unmuted card calls
 *   `abandonAudioFocusRequest` exactly once and transitions
 *   `isUnmuted` to `false`.
 * - (d) Focus loss while unmuted sets `playbackHint = FocusLost` and
 *   KEEPS `isUnmuted == true`.
 * - (e) `release()` abandons focus and unregisters BECOMING_NOISY if
 *   held/registered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedVideoPlayerCoordinatorTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var mockPlayer: ExoPlayer
    private lateinit var holder: SharedVideoPlayer

    @BeforeEach
    fun setUp() {
        // The coordinator's internal scope binds Dispatchers.Main.immediate;
        // unit tests must replace the main dispatcher.
        Dispatchers.setMain(dispatcher)

        // The Android stub jar throws / returns null for chained
        // AudioAttributes.Builder + AudioFocusRequest.Builder calls.
        // Mock the builders so production code's chains return self
        // and `.build()` yields a relaxed mock — verifications care
        // only about the AudioManager interactions, not the builder
        // arguments.
        mockkConstructor(AudioAttributes.Builder::class)
        every { anyConstructed<AudioAttributes.Builder>().setUsage(any()) } answers
            { self as AudioAttributes.Builder }
        every { anyConstructed<AudioAttributes.Builder>().setContentType(any()) } answers
            { self as AudioAttributes.Builder }
        every { anyConstructed<AudioAttributes.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(AudioFocusRequest.Builder::class)
        every { anyConstructed<AudioFocusRequest.Builder>().setAudioAttributes(any()) } answers
            { self as AudioFocusRequest.Builder }
        every {
            anyConstructed<AudioFocusRequest.Builder>().setOnAudioFocusChangeListener(any())
        } answers { self as AudioFocusRequest.Builder }
        every { anyConstructed<AudioFocusRequest.Builder>().build() } returns mockk(relaxed = true)

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        audioManager = mockk(relaxed = true)
        mockPlayer = mockk(relaxed = true)
        holder = mockk(relaxed = true)
        every { holder.player } returns MutableStateFlow<Player?>(mockPlayer).asStateFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `bindMostVisibleVideo never requests audio focus`() =
        runTest(dispatcher) {
            val coordinator = newCoordinator()

            // Bind to one video, then rebind to another (simulating a
            // scroll between two muted videos). NEITHER bind path
            // touches audio focus.
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p2", "u2"))
            advanceUntilIdle()
            coordinator.bindMostVisibleVideo(null)
            advanceUntilIdle()

            verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        }

    @Test
    fun `bindMostVisibleVideo delegates to holder bind setMode attachSurface play`() =
        runTest(dispatcher) {
            val coordinator = newCoordinator()

            coordinator.bindMostVisibleVideo(
                VideoBindingTarget(postId = "post1", playlistUrl = "https://video.cdn/hls/post1.m3u8"),
            )
            advanceUntilIdle()

            verifyOrder {
                holder.setMode(PlaybackMode.FeedPreview)
                holder.bind(playlistUrl = "https://video.cdn/hls/post1.m3u8", posterUrl = null)
                holder.attachSurface()
                holder.play()
            }

            coordinator.release()
        }

    @Test
    fun `bindMostVisibleVideo null pauses and detaches without clearing holder bound url`() =
        runTest(dispatcher) {
            val coordinator = newCoordinator()

            // First bind so there's something to unbind from.
            coordinator.bindMostVisibleVideo(
                VideoBindingTarget(postId = "post1", playlistUrl = "https://video.cdn/hls/post1.m3u8"),
            )
            advanceUntilIdle()
            clearMocks(holder, mockPlayer, answers = false, recordedCalls = true, childMocks = false)

            // Now unbind.
            coordinator.bindMostVisibleVideo(null)
            advanceUntilIdle()

            // Unbind path: pause the existing player + detach the surface.
            verify(exactly = 1) { mockPlayer.pause() }
            verify(exactly = 1) { holder.detachSurface() }

            // Critical instance-transfer property: unbind MUST NOT call holder.bind
            // with a different URL or clearMediaItems — the bound playlist must
            // stay prepared on the holder so a future fullscreen tap (zak.4 / zak.5)
            // can pick up mid-playback without a re-prepare cycle.
            verify(exactly = 0) { holder.bind(any(), any()) }

            coordinator.release()
        }

    @Test
    fun `bindMostVisibleVideo to the same postId is idempotent no-op`() =
        runTest(dispatcher) {
            val coordinator = newCoordinator()
            val target = VideoBindingTarget(postId = "post1", playlistUrl = "https://video.cdn/hls/post1.m3u8")

            coordinator.bindMostVisibleVideo(target)
            advanceUntilIdle()
            clearMocks(holder, mockPlayer, answers = false, recordedCalls = true, childMocks = false)

            // Second bind to the exact same target — should be a no-op
            // (scroll-gated bindMostVisibleVideo distinctUntilChanges on
            // bind id, but a layout recompute that doesn't change the
            // resting post still passes through here; the coordinator's
            // early-return prevents the rebind).
            coordinator.bindMostVisibleVideo(target)
            advanceUntilIdle()

            verify(exactly = 0) { holder.bind(any(), any()) }
            verify(exactly = 0) { holder.setMode(any()) }
            verify(exactly = 0) { holder.attachSurface() }
            verify(exactly = 0) { holder.play() }

            coordinator.release()
        }

    @Test
    fun `toggleMute from muted to unmuted requests focus exactly once`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            val coordinator = newCoordinator()

            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute()
            advanceUntilIdle()

            verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
            assertTrue(coordinator.isUnmuted.value, "expected isUnmuted = true after toggleMute")
        }

    @Test
    fun `toggleMute denies do not transition isUnmuted`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_FAILED
            val coordinator = newCoordinator()

            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute()
            advanceUntilIdle()

            // Focus denied → coordinator stays muted; user can retry.
            assertEquals(false, coordinator.isUnmuted.value)
        }

    @Test
    fun `scroll-away from an unmuted card abandons focus and auto-mutes`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            val coordinator = newCoordinator()

            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute()
            advanceUntilIdle()

            // Rebind to a different post — coordinator MUST abandon
            // focus and transition isUnmuted to false BEFORE the new
            // playback session starts.
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p2", "u2"))
            advanceUntilIdle()

            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
            assertEquals(false, coordinator.isUnmuted.value)
            assertEquals("p2", coordinator.boundPostId.value)
        }

    @Test
    fun `focus loss while unmuted preserves isUnmuted and sets FocusLost hint`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            val coordinator = newCoordinator()
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute()
            advanceUntilIdle()

            assertTrue(coordinator.isUnmuted.value)

            // Fire focus-loss-transient (incoming call / music app gain).
            // Routed through the coordinator's `internal` test hook —
            // `AudioFocusRequest.getOnAudioFocusChangeListener()` isn't
            // exposed on unit-test Android stubs.
            coordinator.handleSystemFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            advanceUntilIdle()

            assertEquals(PlaybackHint.FocusLost, coordinator.playbackHint.value)
            // User intent preserved — stays unmuted so resume() can
            // reacquire focus + replay with sound.
            assertTrue(coordinator.isUnmuted.value, "isUnmuted must survive a transient focus loss")
        }

    @Test
    fun `BECOMING_NOISY broadcast while unmuted surfaces FocusLost hint`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            val coordinator = newCoordinator()
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute() // request focus + register NOISY receiver
            advanceUntilIdle()

            // Fire BECOMING_NOISY (headphones unplug). Routed through
            // the coordinator's `internal` test hook — the broadcast
            // receiver registration on a mocked Context isn't reliably
            // captured by mockk slots since the relaxed Context doesn't
            // dispatch through our every(...) intercept consistently.
            coordinator.notifyBecomingNoisy()
            advanceUntilIdle()

            assertEquals(PlaybackHint.FocusLost, coordinator.playbackHint.value)
            assertTrue(coordinator.isUnmuted.value)
        }

    @Test
    fun `release abandons focus and unregisters NOISY when previously unmuted`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            val coordinator = newCoordinator()
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute()
            advanceUntilIdle()

            coordinator.release()

            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any()) }
            verify(atLeast = 1) { context.unregisterReceiver(any()) }
            // Feed must NOT release the singleton holder — it is process-scoped.
            verify(exactly = 0) { mockPlayer.release() }
            // Coordinator must detach the surface on release.
            verify(exactly = 1) { holder.detachSurface() }
            assertEquals(false, coordinator.isUnmuted.value)
            assertEquals(PlaybackHint.None, coordinator.playbackHint.value)
        }

    @Test
    fun `release without ever unmuting does not call abandonAudioFocus`() =
        runTest(dispatcher) {
            val coordinator = newCoordinator()
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()

            coordinator.release()

            verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any()) }
            // Feed must NOT release the singleton holder — it is process-scoped.
            verify(exactly = 0) { mockPlayer.release() }
            // Coordinator must detach the surface on release.
            verify(exactly = 1) { holder.detachSurface() }
        }

    @Test
    fun `resume after focus loss reacquires focus and clears the hint`() =
        runTest(dispatcher) {
            every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED

            val coordinator = newCoordinator()
            coordinator.bindMostVisibleVideo(VideoBindingTarget("p1", "u1"))
            advanceUntilIdle()
            coordinator.toggleMute()
            advanceUntilIdle()

            coordinator.handleSystemFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
            advanceUntilIdle()
            assertEquals(PlaybackHint.FocusLost, coordinator.playbackHint.value)

            coordinator.resume()
            advanceUntilIdle()

            assertEquals(PlaybackHint.None, coordinator.playbackHint.value)
            assertTrue(coordinator.isUnmuted.value)
            // Initial unmute + resume reacquire = 2 focus requests.
            verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        }

    private fun newCoordinator(): FeedVideoPlayerCoordinator =
        FeedVideoPlayerCoordinator(
            context = context,
            audioManager = audioManager,
            sharedVideoPlayer = holder,
        )
}
