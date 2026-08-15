package net.kikin.nubecita.feature.mediaviewer.impl

import app.cash.turbine.test
import io.github.kikin81.atproto.runtime.XrpcError
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.image.ImageRetrievalException
import net.kikin.nubecita.core.image.ImageSaver
import net.kikin.nubecita.core.image.ImageStorageException
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
import android.net.Uri as AndroidUri

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
    fun `init load with gallery embed transitions to Loaded across all ten images`() =
        runTest(mainDispatcher.dispatcher) {
            // A top-level app.bsky.embed.gallery (up to 10 images) opens in the
            // viewer exactly like images — the VM resolves it via the shared
            // EmbedUi.ImageContainerEmbed and the pager handles N.
            val repo = FakeRepo(Result.success(samplePostWithGallery(10)))
            val vm = newVm(repo, imageIndex = 6)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded
            assertEquals(10, loaded.images.size)
            assertEquals(6, loaded.currentIndex)
        }

    @Test
    fun `init load with gallery coerces an out-of-range imageIndex`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeRepo(Result.success(samplePostWithGallery(6)))
            val vm = newVm(repo, imageIndex = 99)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded
            assertEquals(5, loaded.currentIndex) // size - 1
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
    fun `init load resolves images nested in a RecordWithMedia quote`() =
        runTest(mainDispatcher.dispatcher) {
            // A quote post that carries its own gallery (RecordWithMedia.media)
            // must open in the viewer across all N images, same as a top-level
            // embed — resolved via EmbedUi.imageContainer.
            val media =
                EmbedUi.Gallery(
                    items =
                        (0 until 5)
                            .map {
                                ImageUi(
                                    fullsizeUrl = "https://cdn.bsky.app/img/feed_fullsize/plain/cid$it@jpeg",
                                    thumbUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/cid$it@jpeg",
                                    altText = null,
                                    aspectRatio = 1.0f,
                                )
                            }.toImmutableList(),
                )
            val post =
                samplePost(
                    EmbedUi.RecordWithMedia(
                        record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                        media = media,
                    ),
                )
            val repo = FakeRepo(Result.success(post))
            val vm = newVm(repo, imageIndex = 2)
            vm.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()

            val loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded
            assertEquals(5, loaded.images.size)
            assertEquals(2, loaded.currentIndex)
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

    // ---------- save to gallery ----------

    @Test
    fun `save writes the current page's image, not the first`() =
        runTest(mainDispatcher.dispatcher) {
            // Regression guard for the whole point of putting save in the
            // viewer chrome: the page the user is looking at is the image
            // they mean, so paging must change what gets saved.
            val saver = FakeImageSaver()
            val vm = loadedVm(imageSaver = saver)
            advanceUntilIdle()

            vm.handleEvent(MediaViewerEvent.OnPageChanged(1))
            vm.handleEvent(MediaViewerEvent.OnSaveClick)
            advanceUntilIdle()

            assertEquals(1, saver.calls)
            assertEquals("https://cdn/full-2.jpg", saver.lastUrl)
        }

    @Test
    fun `a second tap while saving does not start a second save`() =
        runTest(mainDispatcher.dispatcher) {
            val saver = FakeImageSaver()
            val vm = loadedVm(imageSaver = saver)
            advanceUntilIdle()

            vm.handleEvent(MediaViewerEvent.OnSaveClick)
            // No advanceUntilIdle between the two: the first save is still in
            // flight, which is exactly the double-tap the guard exists for.
            vm.handleEvent(MediaViewerEvent.OnSaveClick)
            advanceUntilIdle()

            assertEquals(1, saver.calls)
        }

    @Test
    fun `isSaving clears after success and after failure`() =
        runTest(mainDispatcher.dispatcher) {
            val ok = loadedVm(imageSaver = FakeImageSaver())
            advanceUntilIdle()
            ok.handleEvent(MediaViewerEvent.OnSaveClick)
            advanceUntilIdle()
            assertFalse(loaded(ok).isSaving, "isSaving must clear after a successful save")

            val failing =
                loadedVm(imageSaver = FakeImageSaver(result = Result.failure(ImageStorageException("nope"))))
            advanceUntilIdle()
            failing.handleEvent(MediaViewerEvent.OnSaveClick)
            advanceUntilIdle()
            assertFalse(loaded(failing).isSaving, "isSaving must clear after a failed save")
        }

    @Test
    fun `each failure kind maps to its own outcome`() =
        runTest(mainDispatcher.dispatcher) {
            val retrieval =
                loadedVm(
                    imageSaver =
                        FakeImageSaver(result = Result.failure(ImageRetrievalException("https://cdn/full-1.jpg"))),
                )
            advanceUntilIdle()
            retrieval.effects.test {
                retrieval.handleEvent(MediaViewerEvent.OnSaveClick)
                advanceUntilIdle()
                assertEquals(
                    MediaViewerEffect.ShowSaveOutcome(MediaViewerSaveOutcome.RetrievalFailed),
                    awaitItem(),
                )
            }

            val storage =
                loadedVm(imageSaver = FakeImageSaver(result = Result.failure(ImageStorageException("disk full"))))
            advanceUntilIdle()
            storage.effects.test {
                storage.handleEvent(MediaViewerEvent.OnSaveClick)
                advanceUntilIdle()
                assertEquals(
                    MediaViewerEffect.ShowSaveOutcome(MediaViewerSaveOutcome.StorageFailed),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `success emits the saved outcome`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = loadedVm(imageSaver = FakeImageSaver())
            advanceUntilIdle()
            vm.effects.test {
                vm.handleEvent(MediaViewerEvent.OnSaveClick)
                advanceUntilIdle()
                assertEquals(MediaViewerEffect.ShowSaveOutcome(MediaViewerSaveOutcome.Saved), awaitItem())
            }
        }

    @Test
    fun `canSave mirrors the saver's platform support`() =
        runTest(mainDispatcher.dispatcher) {
            val supported = loadedVm(imageSaver = FakeImageSaver(isSupported = true))
            advanceUntilIdle()
            assertTrue(loaded(supported).canSave)

            val unsupported =
                loadedVm(imageSaver = FakeImageSaver(isSupported = false))
            advanceUntilIdle()
            assertFalse(loaded(unsupported).canSave, "canSave must be false where the platform cannot save")
        }

    // ---------- helpers ----------

    private fun newVm(
        repo: PostRepository,
        postUri: String = "at://focus",
        imageIndex: Int = 0,
        imageSaver: ImageSaver = FakeImageSaver(),
    ): MediaViewerViewModel =
        MediaViewerViewModel(
            route = MediaViewerRoute(postUri = postUri, imageIndex = imageIndex),
            postRepository = repo,
            imageSaver = imageSaver,
        )

    /**
     * In-memory [ImageSaver] for driving the save path deterministically.
     */
    private class FakeImageSaver(
        override val isSupported: Boolean = true,
        private val result: Result<AndroidUri> = Result.success(PLACEHOLDER_URI),
    ) : ImageSaver {
        var calls: Int = 0
            private set
        var lastUrl: String? = null
            private set

        override suspend fun saveToGallery(url: String): Result<AndroidUri> {
            calls++
            lastUrl = url
            return result
        }

        private companion object {
            /** `Uri` is Android-only; the VM never reads the value, only success-vs-failure. */
            val PLACEHOLDER_URI: AndroidUri = mockk(relaxed = true)
        }
    }

    /** A VM already settled in [MediaViewerLoadStatus.Loaded] over a two-image post. */
    private fun TestScope.loadedVm(imageSaver: ImageSaver): MediaViewerViewModel =
        newVm(repo = FakeRepo(Result.success(twoImagePost())), imageSaver = imageSaver).also {
            it.handleEvent(MediaViewerEvent.Load)
            advanceUntilIdle()
        }

    private fun loaded(vm: MediaViewerViewModel): MediaViewerLoadStatus.Loaded = vm.uiState.value.loadStatus as MediaViewerLoadStatus.Loaded

    private fun twoImagePost(): PostUi =
        samplePost(
            EmbedUi.Images(
                items =
                    persistentListOf(
                        ImageUi(
                            fullsizeUrl = "https://cdn/full-1.jpg",
                            thumbUrl = null,
                            altText = "first",
                            aspectRatio = 1f,
                        ),
                        ImageUi(
                            fullsizeUrl = "https://cdn/full-2.jpg",
                            thumbUrl = null,
                            altText = "second",
                            aspectRatio = 1f,
                        ),
                    ),
            ),
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
                                // MediaViewer reads `fullsizeUrl` — keep the
                                // fixture's fullsize URL on a `feed_fullsize`
                                // path so a future bug that only reproduces
                                // against the real fullsize variant isn't
                                // masked by a misleading thumb-on-fullsize
                                // placeholder.
                                fullsizeUrl = "https://cdn.bsky.app/img/feed_fullsize/plain/cid$it@jpeg",
                                thumbUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/cid$it@jpeg",
                                altText = altText,
                                aspectRatio = 1.0f,
                            )
                        }.toImmutableList(),
            ),
        )

    private fun samplePostWithGallery(count: Int): PostUi =
        samplePost(
            EmbedUi.Gallery(
                items =
                    (0 until count)
                        .map {
                            ImageUi(
                                fullsizeUrl = "https://cdn.bsky.app/img/feed_fullsize/plain/g$it@jpeg",
                                thumbUrl = "https://cdn.bsky.app/img/feed_thumbnail/plain/g$it@jpeg",
                                altText = null,
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
