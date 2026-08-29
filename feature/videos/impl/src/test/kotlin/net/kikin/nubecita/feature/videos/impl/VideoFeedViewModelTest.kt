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
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.analytics.VideoFeedSeek
import net.kikin.nubecita.core.analytics.VideoSeekOutcome
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.video.SharedVideoPlayer
import net.kikin.nubecita.core.video.playback.DataSaverStatus
import net.kikin.nubecita.core.video.playback.VerticalVideoPlaylistPlayer
import net.kikin.nubecita.core.videofeed.VideoFeedPage
import net.kikin.nubecita.core.videofeed.VideoFeedSource
import net.kikin.nubecita.core.videofeed.VideoFeedSourceFactory
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.videos.api.VideoFeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private val sourceFactory = mockk<VideoFeedSourceFactory> { every { create(any()) } returns source }
    private val pool = mockk<VerticalVideoPlaylistPlayer>(relaxed = true)
    private val shared = mockk<SharedVideoPlayer>(relaxed = true)
    private val dataSaver = mockk<DataSaverStatus>(relaxed = true) // isActive() = false by default → prewarm on
    private val handler = mockk<PostInteractionHandler>(relaxed = true)
    private val cacheState = MutableStateFlow<PersistentMap<String, PostInteractionState>>(persistentMapOf())
    private val interactionsCache =
        mockk<PostInteractionsCache>(relaxed = true) {
            every { state } returns cacheState
        }

    private val postRepository = mockk<PostRepository>()
    private val analytics = mockk<AnalyticsClient>(relaxed = true)

    init {
        // Default: the tapped post cannot be hydrated, so tests that don't care about
        // the recovery path still exercise the plain "open at top" fallback. Tests that
        // DO care stub a success explicitly.
        coEvery { postRepository.getPost(any()) } returns Result.failure(IllegalStateException("not hydratable"))
    }

    private fun vm(
        startPostUri: String? = null,
        authorDid: String? = null,
    ) = VideoFeedViewModel(
        VideoFeed(startPostUri, authorDid),
        sourceFactory,
        postRepository,
        pool,
        shared,
        dataSaver,
        handler,
        interactionsCache,
        analytics,
    )

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
    fun emptyPage_stillPlaysTheTappedVideo() =
        runTest(mainDispatcher.dispatcher) {
            // A page with nothing playable is exactly when recovery matters most: the
            // carousel was showing this post a moment ago. Erroring out would refuse to
            // play a video the user just tapped.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(emptyList(), cursor = null))
            coEvery { postRepository.getPost("tapped") } returns Result.success(videoPost("tapped"))

            val viewModel = vm(startPostUri = "tapped")
            advanceUntilIdle()

            val items = (viewModel.uiState.value.status as VideoFeedStatus.Content).items
            assertEquals(listOf("tapped"), items.map { it.post.id })
            assertEquals(0, viewModel.uiState.value.activeIndex)
            verify { analytics.log(VideoFeedSeek(VideoSeekOutcome.Recovered, 1L)) }
        }

    @Test
    fun emptyPage_withNothingToRecover_isError() =
        runTest(mainDispatcher.dispatcher) {
            // Nothing loaded and nothing hydratable -> Error, not a zero-page pager.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(emptyList(), cursor = null))
            coEvery { postRepository.getPost("gone") } returns Result.failure(IllegalStateException("deleted"))

            val viewModel = vm(startPostUri = "gone")
            advanceUntilIdle()

            assertEquals(VideoFeedStatus.Error, viewModel.uiState.value.status)
        }

    @Test
    fun allLoadedPostsDeleted_isError_notAZeroPagePager() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(2) { videoPost("v$it") }, cursor = null))
            cacheState.value =
                persistentMapOf(
                    "v0" to PostInteractionState(isDeleted = true),
                    "v1" to PostInteractionState(isDeleted = true),
                )

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
    fun seek_opensAtAbsoluteIndex_whenTargetIsOnLaterPage() =
        runTest(mainDispatcher.dispatcher) {
            // page 1 = a,b,c (cursor c1); page 2 = d,e (cursor null). Target "e" is index 4 overall.
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b"), videoPost("c")), cursor = "c1"))
            coEvery { source.loadPage("c1") } returns
                Result.success(VideoFeedPage(listOf(videoPost("d"), videoPost("e")), cursor = null))

            val viewModel = vm(startPostUri = "e")
            advanceUntilIdle()

            val status = viewModel.uiState.value.status
            assertInstanceOf(VideoFeedStatus.Content::class.java, status)
            assertEquals(5, (status as VideoFeedStatus.Content).items.size) // both pages accumulated
            assertEquals(4, viewModel.uiState.value.activeIndex) // absolute index of "e", not 0
            coVerify { source.loadPage("c1") } // it actually paged forward
        }

    @Test
    fun seek_stopsAtFirstPage_whenTargetIsThere() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b")), cursor = "c1"))

            val viewModel = vm(startPostUri = "b")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.activeIndex)
            coVerify(exactly = 0) { source.loadPage("c1") } // no needless second page
        }

    @Test
    fun seek_recoversTappedPost_whenAbsentFromEveryLoadedPage() =
        runTest(mainDispatcher.dispatcher) {
            // PREVIOUSLY asserted activeIndex == 0 here, which pinned the bug as correct:
            // the feed's pages are a SECOND, independent fetch of a live feed and need not
            // contain the tapped post at all (device-confirmed, nubecita-zdv8.16). Opening
            // at the top means playing a video the user never tapped.
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a"), videoPost("b")), cursor = null))
            coEvery { postRepository.getPost("tapped") } returns Result.success(videoPost("tapped"))

            val viewModel = vm(startPostUri = "tapped")
            advanceUntilIdle()

            val items = (viewModel.uiState.value.status as VideoFeedStatus.Content).items
            assertEquals(listOf("tapped", "a", "b"), items.map { it.post.id })
            assertEquals(0, viewModel.uiState.value.activeIndex)
            // The pool must be bound to the LIST THAT INCLUDES the recovered post.
            coVerify { pool.bind(match { it.size == 3 }, 0) }
            verify { analytics.log(VideoFeedSeek(VideoSeekOutcome.Recovered, 1L)) }
        }

    @Test
    fun noStartUri_loadsExactlyOnePage() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = "c1"))

            val viewModel = vm(startPostUri = null)
            advanceUntilIdle()

            assertEquals(0, viewModel.uiState.value.activeIndex)
            coVerify(exactly = 0) { source.loadPage("c1") } // seek short-circuits with no target
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
    fun startPostUri_absentAndUnhydratable_fallsBackToTop_andIsCounted() =
        runTest(mainDispatcher.dispatcher) {
            // The ONLY remaining fallback: the post is absent from every page AND cannot
            // be hydrated (deleted, blocked, no longer visible). Opening at the top is
            // correct here — but it must be counted, never silent, because a silent
            // version of exactly this is how the bug reached a user as a mystery.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(3) { videoPost("v$it") }, cursor = null))
            coEvery { postRepository.getPost("gone") } returns Result.failure(IllegalStateException("deleted"))

            val viewModel = vm(startPostUri = "gone")
            advanceUntilIdle()

            val items = (viewModel.uiState.value.status as VideoFeedStatus.Content).items
            assertEquals(listOf("v0", "v1", "v2"), items.map { it.post.id })
            assertEquals(0, viewModel.uiState.value.activeIndex)
            coVerify { pool.bind(any(), 0) }
            verify { analytics.log(VideoFeedSeek(VideoSeekOutcome.FellBackToTop, 1L)) }
        }

    @Test
    fun startPostUri_hydratedPostCarriesNoVideo_fallsBackToTop() =
        runTest(mainDispatcher.dispatcher) {
            // getPost succeeds but the record has no video embed, so there is nothing to
            // play — it must not be prepended as an unplayable page.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(2) { videoPost("v$it") }, cursor = null))
            coEvery { postRepository.getPost("textonly") } returns Result.success(nonVideoPost("textonly"))

            val viewModel = vm(startPostUri = "textonly")
            advanceUntilIdle()

            val items = (viewModel.uiState.value.status as VideoFeedStatus.Content).items
            assertEquals(listOf("v0", "v1"), items.map { it.post.id })
            assertEquals(0, viewModel.uiState.value.activeIndex)
            verify { analytics.log(VideoFeedSeek(VideoSeekOutcome.FellBackToTop, 1L)) }
        }

    @Test
    fun deletedPostBeforeTarget_doesNotShiftTheOpenedVideo() =
        runTest(mainDispatcher.dispatcher) {
            // applyInteractions drops deleted posts, so the UI list is SHORTER than the
            // loaded list. Addressing the pager against `loaded` while rendering `merged`
            // makes index N denote two different clips: with v0 deleted, target v2 sits at
            // index 2 of `loaded` but index 1 of what the user actually sees.
            coEvery { source.loadPage(null) } returns
                Result.success(VideoFeedPage(List(4) { videoPost("v$it") }, cursor = null))
            cacheState.value = persistentMapOf("v0" to PostInteractionState(isDeleted = true))

            val viewModel = vm(startPostUri = "v2")
            advanceUntilIdle()

            val items = (viewModel.uiState.value.status as VideoFeedStatus.Content).items
            assertEquals(listOf("v1", "v2", "v3"), items.map { it.post.id })
            // Index must address the list the user sees, not the pre-filter one.
            assertEquals(1, viewModel.uiState.value.activeIndex)
            assertEquals("v2", items[viewModel.uiState.value.activeIndex].post.id)
            // ...and the pool must be bound to that SAME list, or the surface plays a
            // different clip than the page the pager settled on.
            coVerify { pool.bind(match { it.size == 3 }, 1) }
        }

    @Test
    fun startPostUri_foundInPage_doesNotHydrate_andCountsResolved() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(3) { videoPost("v$it") }, cursor = null))

            val viewModel = vm(startPostUri = "v1")
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.activeIndex)
            // The happy path must not cost an extra network round-trip.
            coVerify(exactly = 0) { postRepository.getPost(any()) }
            verify { analytics.log(VideoFeedSeek(VideoSeekOutcome.Resolved, 1L)) }
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

    @Test
    fun appendedPage_bindsPoolToTheFilteredList_whenAPostIsDeleted() =
        runTest(mainDispatcher.dispatcher) {
            // The append path must address the pool the same way the first page does.
            // Binding `loaded` here would re-point the pool from the filtered list to the
            // unfiltered one on the FIRST append, so the surface silently drifts one clip
            // out of step with the pager mid-scroll.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(4) { videoPost("v$it") }, cursor = "c1"))
            coEvery { source.loadPage("c1") } returns Result.success(VideoFeedPage(listOf(videoPost("w0")), cursor = null))
            cacheState.value = persistentMapOf("v0" to PostInteractionState(isDeleted = true))

            val viewModel = vm()
            advanceUntilIdle()
            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(1))
            advanceUntilIdle()

            val ids = (viewModel.uiState.value.status as VideoFeedStatus.Content).items.map { it.post.id }
            assertEquals(listOf("v1", "v2", "v3", "w0"), ids)
            // 4 items, not the 5 still sitting in `loaded`.
            coVerify { pool.bind(match { it.size == 4 }, 1) }
        }

    @Test
    fun pagination_triggersOnTheRenderedTail_notTheUnfilteredOne() =
        runTest(mainDispatcher.dispatcher) {
            // 8 loaded, 3 deleted -> 5 rendered. Against the RENDERED size the prefetch
            // threshold fires at index 2; against `loaded.size` it would not fire until
            // index 5, which the pager can never reach (it only has pages 0..4). That is
            // pagination stalling at the tail, not merely firing late.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(8) { videoPost("v$it") }, cursor = "c1"))
            coEvery { source.loadPage("c1") } returns Result.success(VideoFeedPage(listOf(videoPost("w0")), cursor = null))
            cacheState.value =
                persistentMapOf(
                    "v0" to PostInteractionState(isDeleted = true),
                    "v1" to PostInteractionState(isDeleted = true),
                    "v2" to PostInteractionState(isDeleted = true),
                )

            val viewModel = vm()
            advanceUntilIdle()
            assertEquals(5, (viewModel.uiState.value.status as VideoFeedStatus.Content).items.size)

            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(2))
            advanceUntilIdle()

            coVerify { source.loadPage("c1") }
        }

    @Test
    fun togglePlayPause_flipsState_andDrivesPool() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.TogglePlayPause)
            assertTrue(viewModel.uiState.value.isPaused)
            verify { pool.setPaused(true) }

            viewModel.handleEvent(VideoFeedEvent.TogglePlayPause)
            assertFalse(viewModel.uiState.value.isPaused)
            verify { pool.setPaused(false) }
        }

    @Test
    fun swipingToANewPage_clearsPaused() =
        runTest(mainDispatcher.dispatcher) {
            // The pool's settle() resumes playback on promotion, so the UI flag must
            // not lag behind it and render a paused glyph over a playing clip.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(List(3) { videoPost("v$it") }, cursor = null))
            val viewModel = vm()
            advanceUntilIdle()
            viewModel.handleEvent(VideoFeedEvent.TogglePlayPause)
            assertTrue(viewModel.uiState.value.isPaused)

            viewModel.handleEvent(VideoFeedEvent.ActiveIndexChanged(1))
            advanceUntilIdle()

            assertFalse(viewModel.uiState.value.isPaused)
        }

    @Test
    fun doubleTapLike_likesAnUnlikedPost() =
        runTest(mainDispatcher.dispatcher) {
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()

            viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(videoPost("a")))

            verify { handler.onLike(any()) }
        }

    @Test
    fun doubleTapLike_onAnAlreadyLikedPost_doesNotUnlike_evenFromAStaleCapture() =
        runTest(mainDispatcher.dispatcher) {
            // Regression for a real device-only bug: pointerInput(Unit) never restarts,
            // so the page pinned its first lambda and kept reporting an already-liked
            // post as unliked. onLike toggles, so the second double tap unliked. The VM
            // now resolves the post from current state rather than trusting the capture.
            coEvery { source.loadPage(null) } returns Result.success(VideoFeedPage(listOf(videoPost("a")), cursor = null))
            val viewModel = vm()
            advanceUntilIdle()
            cacheState.value =
                persistentMapOf(
                    "a" to PostInteractionState(viewerLikeUri = "at://did:plc:fake/app.bsky.feed.like/1", likeCount = 1),
                )
            advanceUntilIdle()

            // The UI hands over the ORIGINAL, unliked post — a stale capture.
            viewModel.handleEvent(VideoFeedEvent.DoubleTapLike(videoPost("a")))

            verify(exactly = 0) { handler.onLike(any()) }
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
