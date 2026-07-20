package net.kikin.nubecita.feature.videos.impl

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.playback.DataSaverStatus
import net.kikin.nubecita.core.video.playback.VerticalVideoPlaylistPlayer
import net.kikin.nubecita.core.videofeed.VideoFeedPage
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
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
    private val dataSaver = mockk<DataSaverStatus>(relaxed = true) // isActive() = false by default → prewarm on
    private val handler = mockk<PostInteractionHandler>(relaxed = true)
    private val cacheState = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
    private val interactionsCache =
        mockk<PostInteractionsCache>(relaxed = true) {
            every { state } returns cacheState
        }

    private fun vm(startPostUri: String? = null) = VideoFeedViewModel(VideoFeed(startPostUri), source, pool, shared, dataSaver, handler, interactionsCache)

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
    fun init_underDataSaver_disablesPrewarm() =
        runTest(mainDispatcher.dispatcher) {
            every { dataSaver.isActive() } returns true
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))

            vm()
            advanceUntilIdle()

            verify { pool.setPrewarmEnabled(false) }
        }

    @Test
    fun init_withoutDataSaver_keepsPrewarm() =
        runTest(mainDispatcher.dispatcher) {
            every { dataSaver.isActive() } returns false
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))

            vm()
            advanceUntilIdle()

            verify { pool.setPrewarmEnabled(true) }
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
    fun toggleMute_flipsState_andSetsPool() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.ToggleMute)

            assertTrue(viewModel.uiState.value.isMuted)
            verify { pool.setMuted(true) }
        }

    @Test
    fun init_bindsHandlerToVideosSurface() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))

            vm()
            advanceUntilIdle()

            verify { handler.bind(PostSurface.Videos, any()) }
        }

    @Test
    fun authorTapped_emitsNavigateToProfile() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.handleEvent(VideoFeedEvent.AuthorTapped(videoPost("a")))
                assertEquals(VideoFeedEffect.NavigateTo(Profile(handle = "did:plc:fake")), awaitItem())
            }
        }

    @Test
    fun postTapped_emitsNavigateToPostDetail() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.effects.test {
                viewModel.handleEvent(VideoFeedEvent.PostTapped(videoPost("a")))
                assertEquals(VideoFeedEffect.NavigateTo(PostDetailRoute(postUri = "a")), awaitItem())
            }
        }

    @Test
    fun cacheEmission_mergesIntoItems_soCountsStayLive() =
        runTest(mainDispatcher.dispatcher) {
            // Guards a regression that has happened before on a handler migration:
            // dropping this merge makes a like appear to work and then revert.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            cacheState.value =
                persistentMapOf(
                    "a" to PostInteractionState(viewerLikeUri = "at://did:plc:fake/app.bsky.feed.like/1", likeCount = 5),
                )
            advanceUntilIdle()

            val merged = (viewModel.uiState.value.status as VideoFeedStatus.Content).items.first().post
            assertTrue(merged.viewer.isLikedByViewer)
            assertEquals(5, merged.stats.likeCount)
        }

    @Test
    fun firstPage_appliesCacheImmediately_noRawFlicker() =
        runTest(mainDispatcher.dispatcher) {
            // A post already liked on another surface must render liked in the FIRST
            // Content state, not raw-then-corrected once the collector emits.
            cacheState.value =
                persistentMapOf(
                    "a" to PostInteractionState(viewerLikeUri = "at://did:plc:fake/app.bsky.feed.like/1", likeCount = 9),
                )
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))

            val viewModel = vm()
            advanceUntilIdle()

            val first = (viewModel.uiState.value.status as VideoFeedStatus.Content).items.first().post
            assertTrue(first.viewer.isLikedByViewer)
            assertEquals(9, first.stats.likeCount)
        }

    @Test
    fun appendedPage_appliesCacheImmediately() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(4) { videoPost("v$it") }, cursor = "c1"))
            coEvery { source.loadPage("c1") } returns Result.success(VideoFeedPage(listOf(videoPost("w0")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            cacheState.value =
                persistentMapOf(
                    "w0" to PostInteractionState(viewerLikeUri = "at://did:plc:fake/app.bsky.feed.like/2", likeCount = 3),
                )
            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(1))
            advanceUntilIdle()

            val appended = (viewModel.uiState.value.status as VideoFeedStatus.Content).items.first { it.post.id == "w0" }.post
            assertTrue(appended.viewer.isLikedByViewer)
            assertEquals(3, appended.stats.likeCount)
        }

    @Test
    fun firstPage_seedsInteractionsCache() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))

            vm()
            advanceUntilIdle()

            verify { interactionsCache.seed(match { posts -> posts.map { it.id } == listOf("a") }) }
        }

    @Test
    fun startPostUri_opensTheTappedVideo_evenWithNonVideoPostsInThePage() =
        runTest(mainDispatcher.dispatcher) {
            // The original bug: the carousel indexed the UNFILTERED page while the feed
            // compacted non-video posts away, so one non-video post shifted every index
            // after it and the wrong clip opened. Addressing by URI makes the filtering
            // irrelevant.
            coEvery { source.loadPage(null) } returns
                Result.success(
                    VideoFeedPage(listOf(videoPost("v0"), nonVideoPost("x"), videoPost("v1"), videoPost("v2")), cursor = null),
                )

            val viewModel = vm(startPostUri = "v2")
            advanceUntilIdle()

            val items = (viewModel.uiState.value.status as VideoFeedStatus.Content).items
            assertEquals(listOf("v0", "v1", "v2"), items.map { it.post.id })
            assertEquals(2, viewModel.uiState.value.activeIndex)
            coVerify { pool.bind(any(), 2) }
        }

    @Test
    fun startPostUri_absentFromPage_fallsBackToFirstItem() =
        runTest(mainDispatcher.dispatcher) {
            // Trending is live: by the time the feed loads, the tapped post may have
            // fallen out of the page entirely. That must open at the top, not crash.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(3) { videoPost("v$it") }, cursor = null))

            val viewModel = vm(startPostUri = "gone")
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.activeIndex)
            coVerify { pool.bind(any(), 0) }
        }

    @Test
    fun duplicatePostsInAPage_collapseToOneItem() =
        runTest(mainDispatcher.dispatcher) {
            // Duplicates render twice in the carousel AND produce duplicate keys in the
            // pager, which keys on post.id — a lazy layout rejects those.
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b"), videoPost("a")), cursor = null))

            val viewModel = vm()
            advanceUntilIdle()

            val ids = (viewModel.uiState.value.status as VideoFeedStatus.Content).items.map { it.post.id }
            assertEquals(listOf("a", "b"), ids)
            assertEquals(ids.size, ids.toSet().size)
        }

    @Test
    fun appendedPage_dropsPostsAlreadyLoaded() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(4) { videoPost("v$it") }, cursor = "c1"))
            // The appended page repeats v3 — an overlap the appview can legitimately return.
            coEvery { source.loadPage("c1") } returns
                Result.success(VideoFeedPage(listOf(videoPost("v3"), videoPost("w0")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(1))
            advanceUntilIdle()

            val ids = (viewModel.uiState.value.status as VideoFeedStatus.Content).items.map { it.post.id }
            assertEquals(listOf("v0", "v1", "v2", "v3", "w0"), ids)
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
