package net.kikin.nubecita.feature.composer.impl

import android.net.Uri
import app.cash.turbine.test
import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.CidLink
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.moderation.PostAudienceDefaultRepository
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.videoupload.VideoUploadError
import net.kikin.nubecita.core.videoupload.VideoUploadRepository
import net.kikin.nubecita.core.videoupload.VideoUploadState
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.data.QuotePostFetcher
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.readyEmbed
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The composer's video slot: mutual exclusion, the eager-upload lifecycle, and
 * the submit gate.
 *
 * These were verified on a device against the live network before they were
 * unit-tested, which is backwards. The device pass proves it works once; these
 * pin the rules so a later edit cannot quietly undo them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerVideoTest {
    // Main is set to this same dispatcher rather than using
    // @ExtendWith(MainDispatcherExtension): that installs its own scheduler,
    // disjoint from runTest's, so emissions into viewModelScope would never be
    // observed by an assertion here. Mirrors ComposerViewModelTest.
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val uploadedUris = mutableListOf<Uri>()

    /**
     * A FRESH flow per call, matching the real repository's cold contract.
     *
     * An earlier version returned one shared replaying flow and made the retry
     * test fail spuriously: the restarted collector immediately re-received the
     * previous `Failed`. A fake that outlives its call is not the thing under
     * test.
     */
    private val emitters = mutableListOf<MutableSharedFlow<VideoUploadState>>()

    /** The flow backing the most recent `upload(...)`. */
    private val uploadStates: MutableSharedFlow<VideoUploadState>
        get() = emitters.last()

    private val videoUploadRepository =
        object : VideoUploadRepository {
            override fun upload(uri: Uri): Flow<VideoUploadState> {
                uploadedUris += uri
                return MutableSharedFlow<VideoUploadState>(replay = 1, extraBufferCapacity = 8)
                    .also { emitters += it }
            }
        }

    private val postingRepository = mockk<PostingRepository>(relaxed = true)
    private val parentFetchSource = mockk<ParentFetchSource>(relaxed = true)
    private val quotePostFetcher = mockk<QuotePostFetcher>(relaxed = true)
    private val externalLinkMetadataRepository = mockk<ExternalLinkMetadataRepository>(relaxed = true)
    private val sharedMediaStore = mockk<net.kikin.nubecita.core.posting.SharedMediaStore>(relaxed = true)
    private val actorRepository = mockk<ActorRepository>(relaxed = true)
    private val postAudienceDefaultRepository =
        mockk<PostAudienceDefaultRepository>(relaxed = true) {
            every { default } returns MutableStateFlow(PostAudience.DEFAULT)
        }

    private fun newVm(): ComposerViewModel =
        ComposerViewModel(
            route = ComposerRoute(),
            postingRepository = postingRepository,
            parentFetchSource = parentFetchSource,
            quotePostFetcher = quotePostFetcher,
            actorRepository = actorRepository,
            localeProvider =
                object : LocaleProvider {
                    override fun primaryLanguageTag(): String = "en-US"
                },
            postAudienceDefaultRepository = postAudienceDefaultRepository,
            externalLinkMetadataRepository = externalLinkMetadataRepository,
            videoUploadRepository = videoUploadRepository,
            reviewManager = mockk(relaxed = true),
            sharedMediaStore = sharedMediaStore,
            applicationScope = CoroutineScope(testDispatcher),
        )

    private fun uri(): Uri = mockk(relaxed = true)

    private fun attachment() =
        net.kikin.nubecita.core.posting
            .ComposerAttachment(uri = mockk(relaxed = true), mimeType = "image/jpeg")

    private val readyState =
        VideoUploadState.Ready(
            blob =
                Blob(
                    ref = CidLink(link = "bafyreiexamplecidforatestblobreference00000000000000"),
                    mimeType = "video/mp4",
                    size = 4_096L,
                ),
            aspectRatio = AspectRatio(width = 1080, height = 1920),
        )

    // ---- eager upload lifecycle ------------------------------------------

    @Test
    fun `picking a video starts the upload immediately, not on submit`() =
        runTest {
            val vm = newVm()
            val picked = uri()

            vm.handleEvent(ComposerEvent.VideoPicked(picked))

            assertEquals(listOf(picked), uploadedUris, "upload must start on pick")
            assertNotNull(vm.uiState.value.video)
        }

    @Test
    fun `pipeline states are mirrored onto composer state`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))

            uploadStates.emit(VideoUploadState.Compressing(0.5f))
            assertEquals(
                VideoUploadState.Compressing(0.5f),
                vm.uiState.value.video
                    ?.uploadState,
            )

            uploadStates.emit(readyState)
            assertTrue(
                vm.uiState.value.video
                    ?.isReady == true,
            )
        }

    @Test
    fun `removing the video clears the slot`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))

            vm.handleEvent(ComposerEvent.RemoveVideo)

            assertNull(vm.uiState.value.video)
        }

    /**
     * The job is cancelled on remove, so a state that arrives afterwards must
     * not resurrect the slot. Without the uri guard a late emission from a
     * cancelled or replaced job would repopulate it.
     */
    @Test
    fun `a late emission after removal does not resurrect the video`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))
            vm.handleEvent(ComposerEvent.RemoveVideo)

            uploadStates.emit(readyState)

            assertNull(vm.uiState.value.video)
        }

    @Test
    fun `retry restarts the pipeline for the same source`() =
        runTest {
            val vm = newVm()
            val picked = uri()
            vm.handleEvent(ComposerEvent.VideoPicked(picked))
            uploadStates.emit(VideoUploadState.Failed(VideoUploadError.Network("dropped")))

            vm.handleEvent(ComposerEvent.RetryVideoUpload)

            assertEquals(listOf(picked, picked), uploadedUris, "retry must reuse the same uri")
            assertFalse(
                vm.uiState.value.video
                    ?.hasFailed == true,
                "retry resets the failed state",
            )
        }

    @Test
    fun `alt text is stored on the slot`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))

            vm.handleEvent(ComposerEvent.SetVideoAlt("a cat knocking over a glass"))

            assertEquals(
                "a cat knocking over a glass",
                vm.uiState.value.video
                    ?.alt,
            )
        }

    // ---- mutual exclusion ------------------------------------------------

    @Test
    fun `attaching a video clears existing photos`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(attachment(), attachment())))
            assertEquals(2, vm.uiState.value.attachments.size)

            vm.handleEvent(ComposerEvent.VideoPicked(uri()))

            assertTrue(
                vm.uiState.value.attachments
                    .isEmpty(),
                "photos must be cleared",
            )
            assertNotNull(vm.uiState.value.video)
        }

    /**
     * A user who picks six items and sees one appear cannot tell a deliberate
     * constraint from a bug, so the drop announces itself.
     */
    @Test
    fun `clearing other media announces itself`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(attachment())))

            vm.effects.test {
                vm.handleEvent(ComposerEvent.VideoPicked(uri()))
                assertEquals(ComposerEffect.VideoReplacedOtherMedia, awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

    /** Nothing displaced means nothing to explain — no gratuitous snackbar. */
    @Test
    fun `attaching a video with an empty composer announces nothing`() =
        runTest {
            val vm = newVm()

            vm.effects.test {
                vm.handleEvent(ComposerEvent.VideoPicked(uri()))
                expectNoEvents()
                cancelAndConsumeRemainingEvents()
            }
            assertNotNull(vm.uiState.value.video)
        }

    @Test
    fun `photos cannot be added while a video is attached`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))

            vm.handleEvent(ComposerEvent.AddAttachments(listOf(attachment())))

            assertTrue(
                vm.uiState.value.attachments
                    .isEmpty(),
                "video XOR images",
            )
            assertNotNull(vm.uiState.value.video)
        }

    @Test
    fun `a second video replaces the first rather than stacking`() =
        runTest {
            val vm = newVm()
            val first = uri()
            val second = uri()
            vm.handleEvent(ComposerEvent.VideoPicked(first))

            vm.handleEvent(ComposerEvent.VideoPicked(second))

            assertEquals(
                second,
                vm.uiState.value.video
                    ?.uri,
                "the newer video wins",
            )
        }

    // ---- submit gate -----------------------------------------------------

    /**
     * The second of two independent guards against publishing a post whose
     * video was silently dropped — the UI gate is the first.
     */
    @Test
    fun `readyEmbed is null until the upload reaches Ready`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))

            uploadStates.emit(VideoUploadState.Uploading(0.9f))
            assertNull(
                vm.uiState.value.video
                    ?.readyEmbed(),
                "in-flight must not produce an embed",
            )

            uploadStates.emit(VideoUploadState.Failed(VideoUploadError.Network(null)))
            assertNull(
                vm.uiState.value.video
                    ?.readyEmbed(),
                "failed must not produce an embed",
            )
        }

    @Test
    fun `readyEmbed carries the blob, alt and aspect ratio once Ready`() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.VideoPicked(uri()))
            vm.handleEvent(ComposerEvent.SetVideoAlt("described"))

            uploadStates.emit(readyState)

            val embed =
                vm.uiState.value.video
                    ?.readyEmbed()
            assertNotNull(embed)
            assertEquals("described", embed?.alt)
            assertEquals(1080L, embed?.aspectRatio?.width)
            assertEquals(1920L, embed?.aspectRatio?.height)
        }
}
