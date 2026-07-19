package net.kikin.nubecita.feature.videos.impl

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.playback.VerticalVideoPlaylistPlayer
import net.kikin.nubecita.core.videofeed.VideoFeedPage
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.videos.api.VideoFeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Instant

class VideoFeedViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val source = mockk<VideoFeedSource>()
    private val pool = mockk<VerticalVideoPlaylistPlayer>(relaxed = true)
    private val shared = mockk<SharedVideoPlayer>(relaxed = true)

    private fun vm(startIndex: Int = 0) = VideoFeedViewModel(VideoFeed(startIndex), source, pool, shared)

    @Test
    fun init_releasesSharedPlayer_loadsFirstPage_bindsPool() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b")), cursor = "c1"))

            val viewModel = vm()
            advanceUntilIdle()

            verify { shared.release() } // decoder handoff
            val status = viewModel.uiState.value.status
            assertInstanceOf(VideoFeedStatus.Content::class.java, status)
            assertEquals(2, (status as VideoFeedStatus.Content).items.size)
            coVerify { pool.bind(match { it.size == 2 }, 0) }
        }

    @Test
    fun firstPage_withNoVideoPosts_isError() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(nonVideoPost("x")), cursor = null))

            val viewModel = vm()
            advanceUntilIdle()

            assertEquals(VideoFeedStatus.Error, viewModel.uiState.value.status)
        }

    @Test
    fun loadFailure_isError() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.failure(RuntimeException("boom"))

            val viewModel = vm()
            advanceUntilIdle()

            assertEquals(VideoFeedStatus.Error, viewModel.uiState.value.status)
        }

    @Test
    fun activeIndexChanged_drivesPool_andUpdatesState() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(6) { videoPost("v$it") }, cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(2))
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.activeIndex)
            coVerify { pool.onActiveIndexChanged(2) }
        }

    @Test
    fun nearTail_loadsNextPage_appendsAndRebinds() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(4) { videoPost("v$it") }, cursor = "c1"))
            coEvery { source.loadPage("c1") } returns Result.success(VideoFeedPage(List(3) { videoPost("w$it") }, cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            // index 1 is within PREFETCH_THRESHOLD (3) of the size-4 tail → triggers load-more.
            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(1))
            advanceUntilIdle()

            coVerify { source.loadPage("c1") }
            val status = viewModel.uiState.value.status as VideoFeedStatus.Content
            assertEquals(7, status.items.size)
        }

    @Test
    fun startIndex_opensAndBindsAtThatVideo_coercedToBounds() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(5) { videoPost("v$it") }, cursor = null))

            val viewModel = vm(startIndex = 99) // out of bounds → coerced to lastIndex (4)
            advanceUntilIdle()

            assertEquals(4, viewModel.uiState.value.activeIndex)
            coVerify { pool.bind(any(), 4) }
        }

    @Test
    fun toggleMute_flipsState_andSetsPool() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.ToggleMute)

            assertTrue(viewModel.uiState.value.isMuted)
            verify { pool.setMuted(true) }
        }

    // --- fixtures ---

    private fun videoPost(id: String): PostUi =
        post(
            id,
            EmbedUi.Video(posterUrl = null, playlistUrl = "https://cdn.example/$id.m3u8", aspectRatio = 0.56f, durationSeconds = 10, altText = null),
        )

    private fun nonVideoPost(id: String): PostUi = post(id, EmbedUi.Empty)

    private fun post(
        id: String,
        embed: EmbedUi,
    ): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author = AuthorUi(did = "did:plc:fake", handle = "a.bsky.social", displayName = "A", avatarUrl = null),
            createdAt = Instant.parse("2026-07-18T12:00:00Z"),
            text = "",
            facets = persistentListOf(),
            embed = embed,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
