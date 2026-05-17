package net.kikin.nubecita.feature.videoplayer.impl

import androidx.media3.common.PlaybackException
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videoplayer.impl.data.FakeVideoPostResolver
import net.kikin.nubecita.feature.videoplayer.impl.data.ResolvedVideoPost
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class VideoPlayerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val resolver = FakeVideoPostResolver()

    private lateinit var isPlayingFlow: MutableStateFlow<Boolean>
    private lateinit var positionMsFlow: MutableStateFlow<Long>
    private lateinit var durationMsFlow: MutableStateFlow<Long>
    private lateinit var playbackErrorFlow: MutableStateFlow<PlaybackException?>
    private lateinit var boundPlaylistUrlFlow: MutableStateFlow<String?>
    private lateinit var holder: SharedVideoPlayer

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        isPlayingFlow = MutableStateFlow(false)
        positionMsFlow = MutableStateFlow(0L)
        durationMsFlow = MutableStateFlow(0L)
        playbackErrorFlow = MutableStateFlow<PlaybackException?>(null)
        boundPlaylistUrlFlow = MutableStateFlow<String?>(null)

        holder = mockk(relaxed = true)
        every { holder.isPlaying } returns isPlayingFlow.asStateFlow()
        every { holder.positionMs } returns positionMsFlow.asStateFlow()
        every { holder.durationMs } returns durationMsFlow.asStateFlow()
        every { holder.playbackError } returns playbackErrorFlow.asStateFlow()
        every { holder.boundPlaylistUrl } returns boundPlaylistUrlFlow.asStateFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_resolveSucceeds_bindsPlayerSetModeFullscreen_andEntersReady() =
        runTest {
            resolver.stub(
                postUri = AT_URI,
                resolved =
                    ResolvedVideoPost(
                        playlistUrl = "https://video.cdn/hls/a.m3u8",
                        posterUrl = "https://video.cdn/poster/a.jpg",
                        durationSeconds = 30,
                        altText = null,
                    ),
            )

            val vm = newVm()
            runCurrent()

            verify { holder.bind("https://video.cdn/hls/a.m3u8", "https://video.cdn/poster/a.jpg") }
            verify { holder.setMode(PlaybackMode.Fullscreen) }
            verify { holder.attachSurface() }
            verify { holder.play() }
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)
        }

    @Test
    fun init_resolveSucceeds_holderAlreadyBoundToSameUrl_skipsRebind() =
        runTest {
            // Instance-transfer payoff: holder already has the right URL set BEFORE VM creation.
            boundPlaylistUrlFlow.value = "https://video.cdn/hls/a.m3u8"
            resolver.stub(
                postUri = AT_URI,
                resolved =
                    ResolvedVideoPost(
                        playlistUrl = "https://video.cdn/hls/a.m3u8",
                        posterUrl = null,
                        durationSeconds = 30,
                        altText = null,
                    ),
            )

            newVm()
            runCurrent()

            verify(exactly = 0) { holder.bind(any(), any()) }
            verify { holder.setMode(PlaybackMode.Fullscreen) }
            verify { holder.attachSurface() }
            verify { holder.play() }
        }

    @Test
    fun init_resolveFailsWithIOException_entersErrorNetwork() =
        runTest {
            resolver.stubFailure(AT_URI, IOException("disconnected"))

            val vm = newVm()
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is VideoPlayerLoadStatus.Error)
            assertEquals(VideoPlayerError.Network, (status as VideoPlayerLoadStatus.Error).error)
        }

    @Test
    fun retryClicked_afterError_reResolvesAndBinds() =
        runTest {
            resolver.stubFailure(AT_URI, IOException("disconnected"))
            val vm = newVm()
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error)

            // Swap stub to success.
            resolver.stub(
                postUri = AT_URI,
                resolved =
                    ResolvedVideoPost(
                        playlistUrl = "https://video.cdn/hls/a.m3u8",
                        posterUrl = null,
                        durationSeconds = 30,
                        altText = null,
                    ),
            )

            vm.handleEvent(VideoPlayerEvent.RetryClicked)
            runCurrent()

            verify { holder.clearPlaybackError() }
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)
        }

    @Test
    fun retryClicked_doesNotDoubleAttachSurface() =
        runTest {
            // First success: attach happens once.
            stubReady()
            val vm = newVm()
            runCurrent()
            verify(exactly = 1) { holder.attachSurface() }

            // Trigger another successful resolve via Retry — the success
            // branch must NOT call attachSurface again, otherwise the
            // holder refcount drifts and the idle-release timer never
            // fires after onCleared.
            vm.handleEvent(VideoPlayerEvent.RetryClicked)
            runCurrent()

            verify(exactly = 1) { holder.attachSurface() }
        }

    @Test
    fun retryClicked_afterPlaybackError_callsPrepareCurrentWhenAlreadyBound() =
        runTest {
            // Initial bind + Ready.
            stubReady()
            boundPlaylistUrlFlow.value = "https://video.cdn/hls/a.m3u8"
            val vm = newVm()
            runCurrent()
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)

            // Simulate the holder surfacing a playback error.
            val pe = mockk<PlaybackException>(relaxed = true)
            val errorCodeField = PlaybackException::class.java.getField("errorCode")
            errorCodeField.isAccessible = true
            errorCodeField.set(pe, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            playbackErrorFlow.value = pe
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error)

            // Retry: holder is already bound to the same URL, so the VM
            // must ask the player to re-prepare instead of skipping the
            // bind silently.
            vm.handleEvent(VideoPlayerEvent.RetryClicked)
            runCurrent()

            verify { holder.clearPlaybackError() }
            verify { holder.prepareCurrent() }
        }

    @Test
    fun init_resolveSuccess_propagatesAltTextIntoState() =
        runTest {
            resolver.stub(
                postUri = AT_URI,
                resolved =
                    ResolvedVideoPost(
                        playlistUrl = "https://video.cdn/hls/a.m3u8",
                        posterUrl = "https://video.cdn/poster/a.jpg",
                        durationSeconds = 30,
                        altText = "Two cats wrestling on a couch",
                    ),
            )

            val vm = newVm()
            runCurrent()

            assertEquals("Two cats wrestling on a couch", vm.uiState.value.altText)
        }

    @Test
    fun init_resolveFails_doesNotArmChromeAutoHide() =
        runTest {
            resolver.stubFailure(AT_URI, IOException("disconnected"))
            val vm = newVm()
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error)
            assertEquals(true, vm.uiState.value.chromeVisible)

            // No Ready state has been reached, so the auto-hide timer must
            // not have armed during init — chrome stays visible even past
            // the 3s mark, otherwise the retry button would vanish before
            // the user could tap it.
            advanceTimeBy(5_000L)
            runCurrent()
            assertEquals(true, vm.uiState.value.chromeVisible)
        }

    @Test
    fun playPauseClicked_whenPlaying_callsPauseAndResetsAutoHide() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()
            isPlayingFlow.value = true
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.PlayPauseClicked)
            runCurrent()

            verify { holder.pause() }
            assertEquals(true, vm.uiState.value.chromeVisible)
        }

    @Test
    fun playPauseClicked_whenPaused_callsPlay() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()
            isPlayingFlow.value = false
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.PlayPauseClicked)
            runCurrent()

            // play() was called once on init and once now — verify at least 2.
            verify(atLeast = 2) { holder.play() }
        }

    @Test
    fun seekTo_callsHolderSeekTo_andResetsAutoHide() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.SeekTo(positionMs = 12_345L))
            runCurrent()

            verify { holder.seekTo(12_345L) }
            assertEquals(true, vm.uiState.value.chromeVisible)
        }

    @Test
    fun chrome_autoHidesAfter3Seconds() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()
            assertEquals(true, vm.uiState.value.chromeVisible)

            advanceTimeBy(3_000L)
            runCurrent()

            assertEquals(false, vm.uiState.value.chromeVisible)
        }

    @Test
    fun toggleChrome_flipsVisible_andStopsAutoHideWhenHiding() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.ToggleChrome)
            runCurrent()
            assertEquals(false, vm.uiState.value.chromeVisible)

            // Now toggle back on — auto-hide should re-arm.
            vm.handleEvent(VideoPlayerEvent.ToggleChrome)
            runCurrent()
            assertEquals(true, vm.uiState.value.chromeVisible)

            advanceTimeBy(3_000L)
            runCurrent()
            assertEquals(false, vm.uiState.value.chromeVisible)
        }

    @Test
    fun backClicked_emitsNavigateBackEffect() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()

            vm.effects.test {
                vm.handleEvent(VideoPlayerEvent.BackClicked)
                runCurrent()
                assertEquals(VideoPlayerEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun playerError_clearedAfterReachingReady_autoRecoversToReady() =
        runTest {
            // Reach Ready, surfaceAttached = true.
            stubReady()
            val vm = newVm()
            runCurrent()
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)

            // Transient playback error — VM enters Error.
            val pe = mockk<PlaybackException>(relaxed = true)
            val errorCodeField = PlaybackException::class.java.getField("errorCode")
            errorCodeField.isAccessible = true
            errorCodeField.set(pe, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            playbackErrorFlow.value = pe
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error)

            // ExoPlayer internally recovers → STATE_READY clears
            // `_playbackError`. The VM's combine projection must bring
            // loadStatus back to Ready, not strand the user on the
            // error layout.
            playbackErrorFlow.value = null
            runCurrent()
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)
        }

    @Test
    fun resolverError_doesNotAutoRecoverFromHolderFlowEmissions() =
        runTest {
            // Resolver-failure path: surfaceAttached = false, loadStatus = Error.
            resolver.stubFailure(AT_URI, IOException("disconnected"))
            val vm = newVm()
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error)

            // An unrelated holder-flow tick fires combine (e.g. another
            // surface elsewhere paused the player) while playbackError
            // is null. We MUST NOT recover from a resolver-failure Error
            // — no playback has ever started for this screen. The user
            // has to tap Retry to escape this state.
            isPlayingFlow.value = false
            runCurrent()
            assertTrue(
                vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error,
                "resolver-failure Error must persist across combine emissions",
            )
        }

    @Test
    fun playerError_fromHolderFlow_mapsToErrorLoadStatus() =
        runTest {
            stubReady()
            val vm = newVm()
            runCurrent()
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)

            // PlaybackException.errorCode is a public final int field — MockK cannot stub final
            // Java fields, and the real constructor calls SystemClock.elapsedRealtime() which
            // isn't available in JVM unit tests. Use reflection to stamp the desired error code
            // onto a relaxed mock instance.
            val pe = mockk<PlaybackException>(relaxed = true)
            val errorCodeField = PlaybackException::class.java.getField("errorCode")
            errorCodeField.isAccessible = true
            errorCodeField.set(pe, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
            playbackErrorFlow.value = pe
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is VideoPlayerLoadStatus.Error)
            assertEquals(VideoPlayerError.Network, (status as VideoPlayerLoadStatus.Error).error)
        }

    private fun stubReady() {
        resolver.stub(
            postUri = AT_URI,
            resolved =
                ResolvedVideoPost(
                    playlistUrl = "https://video.cdn/hls/a.m3u8",
                    posterUrl = null,
                    durationSeconds = 30,
                    altText = null,
                ),
        )
    }

    private fun newVm(): VideoPlayerViewModel =
        VideoPlayerViewModel(
            route = VideoPlayerRoute(postUri = AT_URI),
            sharedVideoPlayer = holder,
            resolver = resolver,
        )

    private companion object {
        const val AT_URI = "at://did:plc:abc/app.bsky.feed.post/3abc"
    }
}
