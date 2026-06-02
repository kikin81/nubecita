package net.kikin.nubecita.feature.videoplayer.impl

import androidx.media3.common.PlaybackException
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
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
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.video.PlaybackMode
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class VideoPlayerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val postRepository = mockk<PostRepository>()

    private lateinit var isPlayingFlow: MutableStateFlow<Boolean>
    private lateinit var positionMsFlow: MutableStateFlow<Long>
    private lateinit var durationMsFlow: MutableStateFlow<Long>
    private lateinit var playbackErrorFlow: MutableStateFlow<PlaybackException?>
    private lateinit var boundPlaylistUrlFlow: MutableStateFlow<String?>
    private lateinit var videoAspectRatioFlow: MutableStateFlow<Float?>
    private lateinit var holder: SharedVideoPlayer

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        isPlayingFlow = MutableStateFlow(false)
        positionMsFlow = MutableStateFlow(0L)
        durationMsFlow = MutableStateFlow(0L)
        playbackErrorFlow = MutableStateFlow<PlaybackException?>(null)
        boundPlaylistUrlFlow = MutableStateFlow<String?>(null)
        videoAspectRatioFlow = MutableStateFlow<Float?>(null)

        holder = mockk(relaxed = true)
        every { holder.isPlaying } returns isPlayingFlow.asStateFlow()
        every { holder.positionMs } returns positionMsFlow.asStateFlow()
        every { holder.durationMs } returns durationMsFlow.asStateFlow()
        every { holder.playbackError } returns playbackErrorFlow.asStateFlow()
        every { holder.boundPlaylistUrl } returns boundPlaylistUrlFlow.asStateFlow()
        every { holder.videoAspectRatio } returns videoAspectRatioFlow.asStateFlow()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_resolveSucceeds_bindsPlayerSetModeFullscreen_andEntersReady() =
        runTest {
            stubVideo(
                playlistUrl = "https://video.cdn/hls/a.m3u8",
                posterUrl = "https://video.cdn/poster/a.jpg",
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
            stubVideo(playlistUrl = "https://video.cdn/hls/a.m3u8")

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
            stubFailure(IOException("disconnected"))

            val vm = newVm()
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is VideoPlayerLoadStatus.Error)
            assertEquals(VideoPlayerError.Network, (status as VideoPlayerLoadStatus.Error).error)
        }

    @Test
    fun init_postWithoutVideoEmbed_entersError_andDoesNotBind() =
        runTest {
            // getPost succeeds but the post isn't a video post — a non-video
            // URI was routed here. The VM must error out, never bind/play.
            coEvery { postRepository.getPost(AT_URI) } returns
                Result.success(postUi(embed = EmbedUi.Empty))

            val vm = newVm()
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is VideoPlayerLoadStatus.Error)
            assertTrue((status as VideoPlayerLoadStatus.Error).error is VideoPlayerError.Unknown)
            verify(exactly = 0) { holder.bind(any(), any()) }
            verify(exactly = 0) { holder.attachSurface() }
        }

    @Test
    fun init_resolveSuccess_populatesAuthorStatsViewerIntoState() =
        runTest {
            stubVideo(
                author = AUTHOR,
                stats = PostStatsUi(replyCount = 42, repostCount = 128, likeCount = 2400),
                viewer = ViewerStateUi(isLikedByViewer = true, likeUri = "at://did:plc:abc/app.bsky.feed.like/1"),
            )

            val vm = newVm()
            runCurrent()

            assertEquals(AUTHOR, vm.uiState.value.author)
            assertEquals(PostStatsUi(replyCount = 42, repostCount = 128, likeCount = 2400), vm.uiState.value.stats)
            assertEquals(
                ViewerStateUi(isLikedByViewer = true, likeUri = "at://did:plc:abc/app.bsky.feed.like/1"),
                vm.uiState.value.viewer,
            )
        }

    @Test
    fun retryClicked_afterError_reResolvesAndBinds() =
        runTest {
            stubFailure(IOException("disconnected"))
            val vm = newVm()
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is VideoPlayerLoadStatus.Error)

            // Swap stub to success.
            stubVideo(playlistUrl = "https://video.cdn/hls/a.m3u8")

            vm.handleEvent(VideoPlayerEvent.RetryClicked)
            runCurrent()

            verify { holder.clearPlaybackError() }
            assertEquals(VideoPlayerLoadStatus.Ready, vm.uiState.value.loadStatus)
        }

    @Test
    fun retryClicked_doesNotDoubleAttachSurface() =
        runTest {
            // First success: attach happens once.
            stubVideo()
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
            stubVideo()
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
    fun decodedVideoAspectRatio_overridesLexiconHint() =
        runTest {
            // Resolver hands back a 16:9 lexicon hint (the FeedMapping
            // fallback when app.bsky.embed.video#view.aspectRatio is
            // absent).
            stubVideo(aspectRatio = 16f / 9f)
            val vm = newVm()
            runCurrent()
            assertEquals(16f / 9f, vm.uiState.value.aspectRatio)

            // ExoPlayer decodes the first frame and reports the real
            // dimensions (e.g. 9:16 portrait shot). The VM must replace
            // the lexicon hint with the measured value.
            videoAspectRatioFlow.value = 9f / 16f
            runCurrent()
            assertEquals(9f / 16f, vm.uiState.value.aspectRatio)
        }

    @Test
    fun init_resolveSuccess_propagatesAltTextIntoState() =
        runTest {
            stubVideo(
                posterUrl = "https://video.cdn/poster/a.jpg",
                altText = "Two cats wrestling on a couch",
            )

            val vm = newVm()
            runCurrent()

            assertEquals("Two cats wrestling on a couch", vm.uiState.value.altText)
        }

    @Test
    fun init_resolveFails_doesNotArmChromeAutoHide() =
        runTest {
            stubFailure(IOException("disconnected"))
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
            stubVideo()
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
            stubVideo()
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
            stubVideo()
            val vm = newVm()
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.SeekTo(positionMs = 12_345L))
            runCurrent()

            verify { holder.seekTo(12_345L) }
            assertEquals(true, vm.uiState.value.chromeVisible)
        }

    @Test
    fun skipForward_advances10s_andResetsAutoHide() =
        runTest {
            stubVideo()
            val vm = newVm()
            runCurrent()
            positionMsFlow.value = 60_000L
            durationMsFlow.value = 200_000L
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.SkipForward)
            runCurrent()

            verify { holder.seekTo(70_000L) }
            assertEquals(true, vm.uiState.value.chromeVisible)
        }

    @Test
    fun skipForward_clampsAtDuration() =
        runTest {
            stubVideo()
            val vm = newVm()
            runCurrent()
            positionMsFlow.value = 195_000L
            durationMsFlow.value = 200_000L
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.SkipForward)
            runCurrent()

            verify { holder.seekTo(200_000L) }
        }

    @Test
    fun skipBack_rewinds10s() =
        runTest {
            stubVideo()
            val vm = newVm()
            runCurrent()
            positionMsFlow.value = 60_000L
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.SkipBack)
            runCurrent()

            verify { holder.seekTo(50_000L) }
        }

    @Test
    fun skipBack_clampsAtZero() =
        runTest {
            stubVideo()
            val vm = newVm()
            runCurrent()
            positionMsFlow.value = 5_000L
            runCurrent()

            vm.handleEvent(VideoPlayerEvent.SkipBack)
            runCurrent()

            verify { holder.seekTo(0L) }
        }

    @Test
    fun chrome_autoHidesAfter3Seconds() =
        runTest {
            stubVideo()
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
            stubVideo()
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
            stubVideo()
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
            stubVideo()
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
            stubFailure(IOException("disconnected"))
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
            stubVideo()
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

    // --- Stub helpers ---------------------------------------------------

    /** Stub [PostRepository.getPost] to return a video post for [AT_URI]. */
    private fun stubVideo(
        playlistUrl: String = "https://video.cdn/hls/a.m3u8",
        posterUrl: String? = null,
        altText: String? = null,
        aspectRatio: Float = 16f / 9f,
        author: AuthorUi = AUTHOR,
        stats: PostStatsUi = PostStatsUi(),
        viewer: ViewerStateUi = ViewerStateUi(),
    ) {
        coEvery { postRepository.getPost(AT_URI) } returns
            Result.success(
                postUi(
                    embed =
                        EmbedUi.Video(
                            posterUrl = posterUrl,
                            playlistUrl = playlistUrl,
                            aspectRatio = aspectRatio,
                            durationSeconds = 30,
                            altText = altText,
                        ),
                    author = author,
                    stats = stats,
                    viewer = viewer,
                ),
            )
    }

    private fun stubFailure(error: Throwable) {
        coEvery { postRepository.getPost(AT_URI) } returns Result.failure(error)
    }

    private fun postUi(
        embed: EmbedUi,
        author: AuthorUi = AUTHOR,
        stats: PostStatsUi = PostStatsUi(),
        viewer: ViewerStateUi = ViewerStateUi(),
    ): PostUi =
        PostUi(
            id = AT_URI,
            cid = CID,
            author = author,
            createdAt = Instant.parse("2026-05-16T12:00:00Z"),
            text = "Golden hour over the bay.",
            facets = persistentListOf(),
            embed = embed,
            stats = stats,
            viewer = viewer,
            repostedBy = null,
        )

    private fun newVm(): VideoPlayerViewModel =
        VideoPlayerViewModel(
            route = VideoPlayerRoute(postUri = AT_URI),
            sharedVideoPlayer = holder,
            postRepository = postRepository,
        )

    private companion object {
        const val AT_URI = "at://did:plc:abc/app.bsky.feed.post/3abc"
        const val CID = "bafyreifakefakefakefakefakefakefakefakefakefake"
        val AUTHOR =
            AuthorUi(
                did = "did:plc:abc",
                handle = "sana.nubecita.app",
                displayName = "Sana Okafor",
                avatarUrl = null,
            )
    }
}
