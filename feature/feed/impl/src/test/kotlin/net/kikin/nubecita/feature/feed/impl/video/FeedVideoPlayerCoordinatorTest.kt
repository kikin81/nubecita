@file:androidx.annotation.OptIn(UnstableApi::class)

package net.kikin.nubecita.feature.feed.impl.video

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    private lateinit var player: ExoPlayer
    private lateinit var trackSelector: DefaultTrackSelector

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
        player = mockk(relaxed = true)
        trackSelector = mockk(relaxed = true)
        every { trackSelector.buildUponParameters() } returns mockk(relaxed = true)
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
            verify(exactly = 1) { player.release() }
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
            verify(exactly = 1) { player.release() }
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
            player = player,
            trackSelector = trackSelector,
        )
}
