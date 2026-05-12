package net.kikin.nubecita.feature.mediaviewer.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class MediaViewerViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `init load with image embed transitions to Loaded with currentIndex from route`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithImages(3)))
            val vm = newVm(repo, postUri = "at://focus", imageIndex = 1)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is MediaViewerLoadStatus.Loaded)
            val loaded = status as MediaViewerLoadStatus.Loaded
            assertEquals(3, loaded.images.size)
            assertEquals(1, loaded.currentIndex)
            assertTrue(loaded.isChromeVisible)
            assertFalse(loaded.isAltSheetOpen)
        }

    @Test
    fun `init load coerces imageIndex into 0 until images_size`() =
        runTest(mainDispatcher.dispatcher) {
            // Defensive: if a route is constructed with an out-of-range
            // imageIndex (e.g., a stale deep-link after the post lost an
            // image), the VM clamps rather than throwing IOOBE.
            val repo = FakeRepo(Result.success(samplePostWithImages(2)))
            val vm = newVm(repo, imageIndex = 99)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded
            assertEquals(1, loaded.currentIndex) // size - 1
        }

    @Test
    fun `init load with non-image embed surfaces Error(NoImages)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePost(EmbedUi.Empty)))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is MediaViewerLoadStatus.Error)
            assertEquals(MediaViewerError.NoImages, (status as MediaViewerLoadStatus.Error).error)
        }

    @Test
    fun `IOException surfaces Error(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.failure(IOException("offline")))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is MediaViewerLoadStatus.Error)
            assertEquals(MediaViewerError.Network, (status as MediaViewerLoadStatus.Error).error)
        }

    @Test
    fun `NoSessionException surfaces Error(Unauthenticated)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.failure(NoSessionException()))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Error
            assertEquals(MediaViewerError.Unauthenticated, status.error)
        }

    @Test
    fun `XrpcError NotFound surfaces Error(NotFound)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    Result.failure(XrpcError.Unknown(name = "NotFound", message = null, status = 400)),
                )
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Error
            assertEquals(MediaViewerError.NotFound, status.error)
        }

    @Test
    fun `Retry from Error transitions through Loading and back to Error on second failure`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    Result.failure(IOException("first")),
                    Result.failure(IOException("second")),
                )
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is MediaViewerLoadStatus.Error)

            vm.handleEvent(MediaViewerEvent.Retry)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is MediaViewerLoadStatus.Error)
            assertEquals(2, repo.invocations)
        }

    @Test
    fun `Retry from Error to success transitions to Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeRepo(
                    Result.failure(IOException("first")),
                    Result.success(samplePostWithImages(2)),
                )
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is MediaViewerLoadStatus.Error)

            vm.handleEvent(MediaViewerEvent.Retry)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is MediaViewerLoadStatus.Loaded)
        }

    @Test
    fun `OnPageChanged updates currentIndex and resets chrome to visible`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithImages(3)))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()
            // Manually hide chrome so we can verify the page-change reset.
            vm.handleEvent(MediaViewerEvent.OnChromeAutoFadeTimeout)
            assertFalse((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isChromeVisible)

            vm.handleEvent(MediaViewerEvent.OnPageChanged(2))

            val loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded
            assertEquals(2, loaded.currentIndex)
            assertTrue(loaded.isChromeVisible)
        }

    @Test
    fun `OnTapImage toggles chrome visibility`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithImages(1)))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()
            assertTrue((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isChromeVisible)

            vm.handleEvent(MediaViewerEvent.OnTapImage)
            assertFalse((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isChromeVisible)

            vm.handleEvent(MediaViewerEvent.OnTapImage)
            assertTrue((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isChromeVisible)
        }

    @Test
    fun `OnAltBadgeClick opens sheet, OnAltSheetDismiss closes it`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithImages(1, altText = "the cat")))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            vm.handleEvent(MediaViewerEvent.OnAltBadgeClick)
            assertTrue((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isAltSheetOpen)

            vm.handleEvent(MediaViewerEvent.OnAltSheetDismiss)
            assertFalse((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isAltSheetOpen)
        }

    @Test
    fun `OnTapImage while alt sheet is open is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithImages(1, altText = "alt")))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()
            vm.handleEvent(MediaViewerEvent.OnAltBadgeClick)
            assertTrue((vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded).isChromeVisible)

            vm.handleEvent(MediaViewerEvent.OnTapImage)
            // Chrome stays visible; sheet stays open.
            val loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded
            assertTrue(loaded.isChromeVisible)
            assertTrue(loaded.isAltSheetOpen)
        }

    @Test
    fun `OnDismissRequest emits Dismiss effect`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithImages(1)))
            val vm = newVm(repo)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(MediaViewerEvent.OnDismissRequest)
                assertEquals(MediaViewerEffect.Dismiss, awaitItem())
            }
        }

    // ---------- helpers ----------

    private fun newVm(
        repo: PostRepository,
        postUri: String = "at://focus",
        imageIndex: Int = 0,
    ): MediaViewerViewModel =
        MediaViewerViewModel(
            route = MediaViewerRoute(postUri = postUri, imageIndex = imageIndex),
            postRepository = repo,
        )

    private fun samplePost(embed: EmbedUi): PostUi =
        PostUi(
            id = "at://focus",
            cid = "bafyreifake",
            author =
                AuthorUi(
                    did = "did:plc:test",
                    handle = "test.bsky.social",
                    displayName = "Test",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-25T12:00:00Z"),
            text = "sample",
            facets = persistentListOf(),
            embed = embed,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

    private fun samplePostWithImages(
        count: Int,
        altText: String? = null,
    ): PostUi =
        samplePost(
            EmbedUi.Images(
                items =
                    (0 until count)
                        .map {
                            ImageUi(
                                fullsizeUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/cid$it@feed_thumbnail",
                                thumbUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/cid$it@feed_thumbnail",
                                altText = altText,
                                aspectRatio = 1.0f,
                            )
                        }.toImmutableList(),
            ),
        )

    private class FakeRepo(
        results: List<Result<PostUi>>,
    ) : PostRepository {
        constructor(result: Result<PostUi>) : this(listOf(result))

        constructor(first: Result<PostUi>, second: Result<PostUi>) : this(listOf(first, second))

        private val queue = ArrayDeque(results)
        var invocations: Int = 0
            private set

        override suspend fun getPost(uri: String): Result<PostUi> {
            invocations += 1
            return queue.removeFirstOrNull() ?: error("FakeRepo exhausted; provide more results")
        }
    }
}
