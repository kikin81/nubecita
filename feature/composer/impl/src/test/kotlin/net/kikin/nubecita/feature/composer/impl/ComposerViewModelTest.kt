package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.actors.ActorSearchPage
import net.kikin.nubecita.core.moderation.PostAudienceDefaultRepository
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.LinkPreview
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyAudience
import net.kikin.nubecita.core.posting.ReplyRefs
import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.data.QuotePostFetcher
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.ExternalLinkStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi
import net.kikin.nubecita.feature.composer.impl.state.QuoteLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.QuotePostUi
import net.kikin.nubecita.feature.composer.impl.state.isGalleryMissingAlt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ComposerViewModel] covering every scenario from
 * the unified-composer spec's "Unit-test coverage for the composer
 * state machine" requirement, **plus** the new
 * `add-composer-mention-typeahead` shape: text mutations now flow
 * through `vm.textFieldState`, not through `ComposerEvent.TextChanged`.
 *
 * Strategy: real VM, fake [PostingRepository] + [ParentFetchSource]
 * + [ActorRepository] via mockk, assert state transitions
 * via Turbine on `uiState` and effects via Turbine on `effects`. No
 * Compose harness needed — the VM is the unit under test, and
 * `TextFieldState` is JVM-friendly so we can mutate it directly.
 *
 * Coroutine setup: Dispatchers.Main is set to
 * UnconfinedTestDispatcher so `viewModelScope.launch { ... }` runs
 * inline up to the first true suspend, making state transitions
 * observable in test-time without `advanceUntilIdle()` ceremony.
 *
 * The dedicated typeahead pipeline tests (mapLatest cancellation,
 * debounce, distinctUntilChanged, NoResults vs Suggestions, error
 * collapses to Idle) live in `ComposerViewModelTypeaheadTest` (next
 * commit).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposerViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val postingRepository = mockk<PostingRepository>()
    private val parentFetchSource = mockk<ParentFetchSource>()
    private val quotePostFetcher = mockk<QuotePostFetcher>()

    // Default: no link preview (relaxed → fetch/downloadThumb return null). The
    // link-card tests override `fetch` per case.
    private val externalLinkMetadataRepository = mockk<ExternalLinkMetadataRepository>(relaxed = true)

    // Seeds the composer's audience from PostAudience.DEFAULT; setDefault is a
    // relaxed no-op (the save-as-default path is exercised separately).
    private val postAudienceDefaultRepository =
        mockk<PostAudienceDefaultRepository>(relaxed = true) {
            every { default } returns MutableStateFlow(PostAudience.DEFAULT)
        }

    // The typeahead repo is exercised by ComposerViewModelTypeaheadTest;
    // here we install a fake that returns empty so the snapshot
    // collector's typeahead pipeline does not interfere with the
    // assertions in this suite.
    private val actorRepository =
        object : ActorRepository {
            override suspend fun searchTypeahead(
                query: String,
                limit: Int,
            ): Result<List<net.kikin.nubecita.data.models.ActorUi>> = Result.success(emptyList())

            override suspend fun searchActors(
                query: String,
                cursor: String?,
                limit: Int,
            ): Result<ActorSearchPage> = error("unused")

            override fun getActor(did: String): kotlinx.coroutines.flow.Flow<net.kikin.nubecita.data.models.ActorUi?> = kotlinx.coroutines.flow.flowOf(null)

            override fun recentActors(
                selfDid: String?,
                limit: Int,
            ): kotlinx.coroutines.flow.Flow<List<net.kikin.nubecita.data.models.ActorUi>> = kotlinx.coroutines.flow.flowOf(emptyList())
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun newPostMode_initialState_isEmptyAndIdle() =
        runTest {
            val vm = newVm(replyToUri = null)

            val state = vm.uiState.value
            assertEquals("", vm.textFieldState.text.toString())
            assertEquals(0, state.graphemeCount)
            assertEquals(false, state.isOverLimit)
            assertTrue(state.attachments.isEmpty())
            assertNull(state.replyToUri)
            assertNull(state.replyParentLoad)
            assertEquals(ComposerSubmitStatus.Idle, state.submitStatus)
        }

    @Test
    fun replyMode_loadingPath_transitionsToLoaded() =
        runTest {
            // Gate the fetch with a CompletableDeferred so we can
            // observe the intermediate Loading state before completing
            // it. Without this, UnconfinedTestDispatcher resolves the
            // mockk return synchronously and the test would skip
            // straight to Loaded — failing to assert the spec's
            // Loading → Loaded transition.
            val parentPost = aParentPostUi()
            val gate = CompletableDeferred<Result<ParentPostUi>>()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = PARENT_URI)

            assertEquals(PARENT_URI, vm.uiState.value.replyToUri)
            assertEquals(ParentLoadStatus.Loading, vm.uiState.value.replyParentLoad)

            gate.complete(Result.success(parentPost))

            assertEquals(ParentLoadStatus.Loaded(parentPost), vm.uiState.value.replyParentLoad)
        }

    @Test
    fun replyMode_failedPath_transitionsToFailed() =
        runTest {
            val cause = ComposerError.Network(RuntimeException("dns"))
            val gate = CompletableDeferred<Result<ParentPostUi>>()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = PARENT_URI)

            assertEquals(ParentLoadStatus.Loading, vm.uiState.value.replyParentLoad)

            gate.complete(Result.failure(cause))

            assertEquals(ParentLoadStatus.Failed(cause), vm.uiState.value.replyParentLoad)
        }

    @Test
    fun replyMode_parentNotReplyable_emitsShowError() =
        runTest {
            // Threadgate defence: the parent resolved but the appview says this
            // viewer can't reply (gate changed since the user tapped reply).
            val gate = CompletableDeferred<Result<ParentPostUi>>()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = PARENT_URI)

            vm.effects.test {
                gate.complete(Result.success(aParentPostUi(canViewerReply = false)))
                assertEquals(ComposerEffect.ShowError(ComposerError.ReplyNotAllowed), awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun replyMode_parentNotReplyable_blocksSubmit() =
        runTest {
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } returns
                Result.success(aParentPostUi(canViewerReply = false))

            val vm = newVm(replyToUri = PARENT_URI)
            setComposerText(vm, "a reply that should never be sent")

            vm.handleEvent(ComposerEvent.Submit)

            // The gate blocks submit: no network create, status stays Idle.
            coVerify(exactly = 0) { postingRepository.createPost(any(), any(), any()) }
            assertEquals(ComposerSubmitStatus.Idle, vm.uiState.value.submitStatus)
        }

    @Test
    fun textChange_updatesGraphemeCountAndOverLimitFlag() =
        runTest {
            val vm = newVm(replyToUri = null)

            setComposerText(vm, "hello")
            assertEquals("hello", vm.textFieldState.text.toString())
            assertEquals(5, vm.uiState.value.graphemeCount)
            assertEquals(false, vm.uiState.value.isOverLimit)

            setComposerText(vm, "a".repeat(301))
            assertEquals(301, vm.uiState.value.graphemeCount)
            assertEquals(true, vm.uiState.value.isOverLimit)
        }

    @Test
    fun graphemeCounting_bmpEmojiCountsAsOneGrapheme() =
        runTest {
            // 🎉 — single BMP codepoint, 2 UTF-16 code units. text.length
            // would say 2; graphemeCount must say 1. (ZWJ sequences are
            // platform-Unicode-version-dependent and tested separately
            // in GraphemeCounterTest with a comment about the JVM/Android
            // skew. This test exercises the integration through the
            // VM's snapshotFlow collector using a platform-stable emoji.)
            val vm = newVm(replyToUri = null)

            setComposerText(vm, "🎉")

            assertEquals(1, vm.uiState.value.graphemeCount)
        }

    @Test
    fun addAttachments_capsAtTen() =
        runTest {
            val vm = newVm(replyToUri = null)

            vm.handleEvent(ComposerEvent.AddAttachments(List(6) { att() }))
            assertEquals(6, vm.uiState.value.attachments.size)

            // 6 + 6 = 12 requested, but the reducer caps at MAX_ATTACHMENTS (10).
            vm.handleEvent(ComposerEvent.AddAttachments(List(6) { att() }))
            assertEquals(10, vm.uiState.value.attachments.size)
        }

    @Test
    fun moveAttachment_reordersPreservingOthers_andIgnoresOutOfRangeOrNoOp() =
        runTest {
            val vm = newVm(replyToUri = null)
            val a = att()
            val b = att()
            val c = att()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(a, b, c)))

            // Move first to last: [a, b, c] -> [b, c, a].
            vm.handleEvent(ComposerEvent.MoveAttachment(from = 0, to = 2))
            assertEquals(
                listOf(b, c, a),
                vm.uiState.value.attachments
                    .toList(),
            )

            // Out-of-range and no-op moves are silently ignored.
            vm.handleEvent(ComposerEvent.MoveAttachment(from = 0, to = 99))
            vm.handleEvent(ComposerEvent.MoveAttachment(from = 1, to = 1))
            assertEquals(
                listOf(b, c, a),
                vm.uiState.value.attachments
                    .toList(),
            )
        }

    @Test
    fun openAltEditor_setsTarget_closeClears_andOutOfRangeIgnored() =
        runTest {
            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att(), att(), att())))

            vm.handleEvent(ComposerEvent.OpenAltEditor(2))
            assertEquals(2, vm.uiState.value.altEditTarget)

            vm.handleEvent(ComposerEvent.CloseAltEditor)
            assertNull(vm.uiState.value.altEditTarget)

            vm.handleEvent(ComposerEvent.OpenAltEditor(99))
            assertNull(vm.uiState.value.altEditTarget)
        }

    @Test
    fun setAltText_updatesOnlyThatPhoto_andPersistsAcrossReorder() =
        runTest {
            val vm = newVm(replyToUri = null)
            val a = att()
            val b = att()
            val c = att()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(a, b, c)))

            vm.handleEvent(ComposerEvent.SetAltText(index = 1, text = "the middle one"))
            assertEquals(
                listOf("", "the middle one", ""),
                vm.uiState.value.attachments
                    .map { it.alt },
            )

            // Reorder the described photo to the front — its alt rides along.
            vm.handleEvent(ComposerEvent.MoveAttachment(from = 1, to = 0))
            assertEquals(
                "the middle one",
                vm.uiState.value.attachments[0]
                    .alt,
            )
        }

    @Test
    fun galleryRequiresAltOnEveryPhoto_butImagesDoNot() =
        runTest {
            val vm = newVm(replyToUri = null)

            // 4 images, all blank alt — an images embed, not gated.
            vm.handleEvent(ComposerEvent.AddAttachments(List(4) { att() }))
            assertFalse(vm.uiState.value.isGalleryMissingAlt)

            // 5th image promotes to a gallery → now every photo needs alt.
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att())))
            assertTrue(vm.uiState.value.isGalleryMissingAlt)

            // Submit is gated: with a blank-alt gallery, Submit is a no-op.
            vm.handleEvent(ComposerEvent.Submit)
            assertEquals(ComposerSubmitStatus.Idle, vm.uiState.value.submitStatus)

            // Describe all 5 → gate lifts.
            repeat(5) { i -> vm.handleEvent(ComposerEvent.SetAltText(index = i, text = "desc $i")) }
            assertFalse(vm.uiState.value.isGalleryMissingAlt)
        }

    @Test
    fun demotingGalleryBelowFive_liftsTheAltGate() =
        runTest {
            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.AddAttachments(List(5) { att() }))
            assertTrue(vm.uiState.value.isGalleryMissingAlt)

            // Remove one → 4 images (images embed) → no longer gated, even blank.
            vm.handleEvent(ComposerEvent.RemoveAttachment(0))
            assertFalse(vm.uiState.value.isGalleryMissingAlt)
        }

    @Test
    fun removeAttachment_removesAtIndex_andSilentlyIgnoresInvalidIndex() =
        runTest {
            val vm = newVm(replyToUri = null)
            val first = att()
            val second = att()
            val third = att()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(first, second, third)))

            vm.handleEvent(ComposerEvent.RemoveAttachment(1))

            assertEquals(2, vm.uiState.value.attachments.size)
            assertEquals(
                listOf(first, third),
                vm.uiState.value.attachments
                    .toList(),
            )

            vm.handleEvent(ComposerEvent.RemoveAttachment(99))
            assertEquals(2, vm.uiState.value.attachments.size)
        }

    @Test
    fun submit_idleToSubmittingToSuccess_emitsOnSubmitSuccess() =
        runTest {
            val newPostUri = AtUri("at://did:plc:me/app.bsky.feed.post/new")
            coEvery {
                postingRepository.createPost(text = "hello", attachments = emptyList(), replyTo = null)
            } returns Result.success(newPostUri)

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "hello")

            vm.effects.test {
                vm.handleEvent(ComposerEvent.Submit)
                assertEquals(
                    ComposerEffect.OnSubmitSuccess(newPostUri = newPostUri, replyToUri = null),
                    awaitItem(),
                )
                cancelAndConsumeRemainingEvents()
            }
            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
        }

    @Test
    fun submit_idleToSubmittingToError_onRepoFailure() =
        runTest {
            val cause = ComposerError.Network(RuntimeException("offline"))
            coEvery {
                postingRepository.createPost(text = "hi", attachments = emptyList(), replyTo = null)
            } returns Result.failure(cause)

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "hi")
            vm.handleEvent(ComposerEvent.Submit)

            assertEquals(ComposerSubmitStatus.Error(cause), vm.uiState.value.submitStatus)
        }

    @Test
    fun submit_isNoOp_whenOverLimit() =
        runTest {
            val vm = newVm(replyToUri = null)
            setComposerText(vm, "a".repeat(301))
            assertEquals(true, vm.uiState.value.isOverLimit)

            vm.handleEvent(ComposerEvent.Submit)

            assertEquals(ComposerSubmitStatus.Idle, vm.uiState.value.submitStatus)
            coVerify(exactly = 0) { postingRepository.createPost(any(), any(), any()) }
        }

    @Test
    fun submit_isNoOp_whenReplyParentNotLoaded() =
        runTest {
            val gate = CompletableDeferred<Result<ParentPostUi>>()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = PARENT_URI)
            setComposerText(vm, "ok")
            assertEquals(ParentLoadStatus.Loading, vm.uiState.value.replyParentLoad)

            vm.handleEvent(ComposerEvent.Submit)

            assertEquals(ComposerSubmitStatus.Idle, vm.uiState.value.submitStatus)
            coVerify(exactly = 0) { postingRepository.createPost(any(), any(), any()) }
            gate.complete(Result.failure(RuntimeException("test cleanup")))
        }

    @Test
    fun retryFromError_reEntersSubmittingThenSuccess() =
        runTest {
            val newPostUri = AtUri("at://did:plc:me/app.bsky.feed.post/retry")
            coEvery {
                postingRepository.createPost(text = "ok", attachments = emptyList(), replyTo = null)
            } returnsMany
                listOf(
                    Result.failure(ComposerError.Network(RuntimeException("first try"))),
                    Result.success(newPostUri),
                )

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "ok")
            vm.handleEvent(ComposerEvent.Submit)
            assertTrue(vm.uiState.value.submitStatus is ComposerSubmitStatus.Error)

            vm.handleEvent(ComposerEvent.Submit)
            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
        }

    @Test
    fun submit_attachmentOnly_succeedsEvenWithBlankText() =
        runTest {
            // Image-only post path: text is blank but attachments are
            // non-empty. canSubmit's hasContent gate must accept this
            // (otherwise users couldn't post photos without captions).
            val newPostUri = AtUri("at://did:plc:me/app.bsky.feed.post/photo")
            val attachment = att()
            coEvery {
                postingRepository.createPost(text = "", attachments = listOf(attachment), replyTo = null)
            } returns Result.success(newPostUri)

            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(attachment)))
            assertTrue(vm.textFieldState.text.isBlank())
            assertEquals(1, vm.uiState.value.attachments.size)

            vm.handleEvent(ComposerEvent.Submit)

            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
        }

    @Test
    fun draftMutations_areIgnored_whileSubmitting() =
        runTest {
            // Submit captures the snapshot at call time. If
            // AddAttachments / RemoveAttachment kept mutating during
            // the in-flight submit, the UI would diverge from what's
            // actually being posted — and on Success the user would
            // see content they think was posted but wasn't. Reducer
            // must reject draft mutations while submitStatus is
            // Submitting.
            //
            // Note on text: with the TextFieldState migration, text
            // mutations are gated at the UI layer via
            // OutlinedTextField's `enabled = false` (the IME can't
            // write to a disabled field). The VM's snapshotFlow
            // collector still fires if anyone programmatically
            // mutates textFieldState, so this test exercises only
            // the event-dispatched draft mutations (attachments) —
            // which the reducer is responsible for gating.
            val newPostUri = AtUri("at://did:plc:me/app.bsky.feed.post/snap")
            val gate = CompletableDeferred<Result<AtUri>>()
            coEvery {
                postingRepository.createPost(text = "snapshot", attachments = emptyList(), replyTo = null)
            } coAnswers { gate.await() }

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "snapshot")
            vm.handleEvent(ComposerEvent.Submit)
            assertEquals(ComposerSubmitStatus.Submitting, vm.uiState.value.submitStatus)

            // Try to mutate attachments mid-submit — should be no-ops.
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att())))
            vm.handleEvent(ComposerEvent.RemoveAttachment(0))

            assertEquals(0, vm.uiState.value.attachments.size)

            gate.complete(Result.success(newPostUri))
            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
        }

    @Test
    fun replyMode_submitCarriesParentAndRootRefs() =
        runTest {
            val parentPost = aParentPostUi()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } returns Result.success(parentPost)
            val newPostUri = AtUri("at://did:plc:me/app.bsky.feed.post/reply")
            coEvery {
                postingRepository.createPost(
                    text = "reply",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parentPost.parentRef, root = parentPost.rootRef),
                )
            } returns Result.success(newPostUri)

            val vm = newVm(replyToUri = PARENT_URI)
            setComposerText(vm, "reply")
            vm.handleEvent(ComposerEvent.Submit)

            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
            coVerify {
                postingRepository.createPost(
                    text = "reply",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parentPost.parentRef, root = parentPost.rootRef),
                )
            }
        }

    @Test
    fun replyMode_submitSuccess_emitEffectCarriesReplyToUri() =
        // Pin: the effect must carry the route's replyToUri so the
        // host (FeedScreen via LocalComposerSubmitEvents) can choose
        // "Reply sent" snackbar copy + dispatch the optimistic
        // replyCount + 1 on the parent. Reply-mode test counterpart
        // to submit_idleToSubmittingToSuccess_emitsOnSubmitSuccess
        // (which covers the new-post replyToUri = null case).
        runTest {
            val parentPost = aParentPostUi()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } returns Result.success(parentPost)
            val newPostUri = AtUri("at://did:plc:me/app.bsky.feed.post/reply")
            coEvery {
                postingRepository.createPost(
                    text = "reply",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parentPost.parentRef, root = parentPost.rootRef),
                )
            } returns Result.success(newPostUri)

            val vm = newVm(replyToUri = PARENT_URI)
            setComposerText(vm, "reply")

            vm.effects.test {
                vm.handleEvent(ComposerEvent.Submit)
                assertEquals(
                    ComposerEffect.OnSubmitSuccess(newPostUri = newPostUri, replyToUri = PARENT_URI),
                    awaitItem(),
                )
                cancelAndConsumeRemainingEvents()
            }
        }

    // ---------- langs ----------

    @Test
    fun initialState_hasNullSelectedLangs() =
        runTest {
            val vm = newVm(replyToUri = null)
            assertEquals(null, vm.uiState.value.selectedLangs)
        }

    @Test
    fun deviceLocaleTag_reflectsInjectedLocaleProvider() {
        val vm = newVm(replyToUri = null, deviceLocaleTag = "ja-JP")
        assertEquals("ja-JP", vm.deviceLocaleTag)
    }

    @Test
    fun languageSelectionConfirmed_singleTag_updatesState() =
        runTest {
            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = listOf("ja-JP")))
            assertEquals(listOf("ja-JP"), vm.uiState.value.selectedLangs)
        }

    @Test
    fun languageSelectionConfirmed_multipleTags_updatesState() =
        runTest {
            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = listOf("ja-JP", "en-US", "es-MX")))
            assertEquals(listOf("ja-JP", "en-US", "es-MX"), vm.uiState.value.selectedLangs)
        }

    @Test
    fun languageSelectionConfirmed_emptyList_isHonoredAsExplicitOverride() =
        runTest {
            // Explicit empty != null. Caller is saying "I want no langs
            // on this post"; reducer must honor that distinct from the
            // no-touch null state.
            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = emptyList()))
            assertEquals(emptyList<String>(), vm.uiState.value.selectedLangs)
        }

    @Test
    fun languageSelectionConfirmed_overCap_isNoOp() =
        runTest {
            // The picker UI defends with disabled checkboxes; the
            // reducer defends defensively for any caller that bypasses
            // the UI or sends a malformed event.
            val vm = newVm(replyToUri = null)
            val before = vm.uiState.value.selectedLangs
            vm.handleEvent(
                ComposerEvent.LanguageSelectionConfirmed(tags = listOf("a", "b", "c", "d")),
            )
            assertEquals(before, vm.uiState.value.selectedLangs)
        }

    @Test
    fun submit_withNullSelectedLangs_callsCreatePostWithNullLangs() =
        runTest {
            coEvery {
                postingRepository.createPost(
                    text = any(),
                    attachments = any(),
                    replyTo = any(),
                    langs = any(),
                )
            } returns Result.success(AtUri("at://did:plc:me/app.bsky.feed.post/lang"))
            val vm = newVm(replyToUri = null)
            setComposerText(vm, "hello")
            vm.handleEvent(ComposerEvent.Submit)
            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
            coVerify {
                postingRepository.createPost(
                    text = "hello",
                    attachments = emptyList(),
                    replyTo = null,
                    langs = null,
                )
            }
        }

    @Test
    fun submit_withExplicitSelectedLangs_callsCreatePostWithThoseLangs() =
        runTest {
            coEvery {
                postingRepository.createPost(
                    text = any(),
                    attachments = any(),
                    replyTo = any(),
                    langs = any(),
                )
            } returns Result.success(AtUri("at://did:plc:me/app.bsky.feed.post/lang"))
            val vm = newVm(replyToUri = null)
            setComposerText(vm, "konnichiwa")
            vm.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags = listOf("ja-JP", "en-US")))
            vm.handleEvent(ComposerEvent.Submit)
            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
            coVerify {
                postingRepository.createPost(
                    text = "konnichiwa",
                    attachments = emptyList(),
                    replyTo = null,
                    langs = listOf("ja-JP", "en-US"),
                )
            }
        }

    // ---------- audience (nubecita-33bw.5) ----------

    @Test
    fun audienceSelectionConfirmed_updatesState() =
        runTest {
            val vm = newVm(replyToUri = null)
            val audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false)

            vm.handleEvent(ComposerEvent.AudienceSelectionConfirmed(audience, saveAsDefault = false))

            assertEquals(audience, vm.uiState.value.audience)
        }

    @Test
    fun initialAudience_isSeededFromTheSyncedDefault() =
        runTest {
            val saved =
                PostAudience(ReplyAudience.Combination(followers = true, following = false, mentioned = false), allowQuotes = false)
            every { postAudienceDefaultRepository.default } returns MutableStateFlow(saved)

            val vm = newVm(replyToUri = null)

            assertEquals(saved, vm.uiState.value.audience)
        }

    @Test
    fun submit_passesSelectedAudienceToCreatePost() =
        runTest {
            val audience = PostAudience(ReplyAudience.Nobody, allowQuotes = true)
            coEvery {
                postingRepository.createPost(text = "hi", attachments = emptyList(), replyTo = null, langs = null, audience = audience)
            } returns Result.success(AtUri("at://did:plc:me/app.bsky.feed.post/x"))

            val vm = newVm(replyToUri = null)
            vm.handleEvent(ComposerEvent.AudienceSelectionConfirmed(audience, saveAsDefault = false))
            setComposerText(vm, "hi")
            vm.handleEvent(ComposerEvent.Submit)

            coVerify {
                postingRepository.createPost(text = "hi", attachments = emptyList(), replyTo = null, langs = null, audience = audience)
            }
        }

    @Test
    fun audienceSelectionConfirmed_withSaveAsDefault_persistsTheDefault() =
        runTest {
            val audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false)
            val vm = newVm(replyToUri = null)

            vm.handleEvent(ComposerEvent.AudienceSelectionConfirmed(audience, saveAsDefault = true))

            coVerify { postAudienceDefaultRepository.setDefault(audience) }
        }

    @Test
    fun saveAsDefaultFailure_emitsShowAudienceSaveError() =
        runTest {
            val audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false)
            coEvery { postAudienceDefaultRepository.setDefault(audience) } throws RuntimeException("offline")
            val vm = newVm(replyToUri = null)

            vm.effects.test {
                vm.handleEvent(ComposerEvent.AudienceSelectionConfirmed(audience, saveAsDefault = true))
                assertEquals(ComposerEffect.ShowAudienceSaveError, awaitItem())
            }
        }

    // ---------- harness ----------

    @Test
    fun quoteMode_loadingPath_transitionsToLoaded() =
        runTest {
            val quote = aQuotePostUi()
            val gate = CompletableDeferred<Result<QuotePostUi>>()
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)

            assertEquals(QUOTE_URI, vm.uiState.value.quotePostUri)
            assertEquals(QuoteLoadStatus.Loading, vm.uiState.value.quotePostLoad)

            gate.complete(Result.success(quote))

            assertEquals(QuoteLoadStatus.Loaded(quote), vm.uiState.value.quotePostLoad)
        }

    @Test
    fun quoteMode_failedPath_transitionsToFailed() =
        runTest {
            val cause = ComposerError.Network(RuntimeException("dns"))
            val gate = CompletableDeferred<Result<QuotePostUi>>()
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)

            assertEquals(QuoteLoadStatus.Loading, vm.uiState.value.quotePostLoad)

            gate.complete(Result.failure(cause))

            assertEquals(QuoteLoadStatus.Failed(cause), vm.uiState.value.quotePostLoad)
        }

    @Test
    fun quoteMode_retry_afterFailure_reloads() =
        runTest {
            val quote = aQuotePostUi()
            var calls = 0
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } coAnswers {
                if (calls++ == 0) {
                    Result.failure(ComposerError.Network(RuntimeException("dns")))
                } else {
                    Result.success(quote)
                }
            }

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)
            assertTrue(vm.uiState.value.quotePostLoad is QuoteLoadStatus.Failed)

            vm.handleEvent(ComposerEvent.RetryQuoteLoad)

            assertEquals(QuoteLoadStatus.Loaded(quote), vm.uiState.value.quotePostLoad)
        }

    @Test
    fun quoteMode_dismiss_clearsQuote() =
        runTest {
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns Result.success(aQuotePostUi())

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)
            assertTrue(vm.uiState.value.quotePostLoad is QuoteLoadStatus.Loaded)

            vm.handleEvent(ComposerEvent.RemoveQuote)

            assertNull(vm.uiState.value.quotePostUri)
            assertNull(vm.uiState.value.quotePostLoad)
        }

    @Test
    fun quoteMode_emptyTextQuote_submits_passingQuoteRef() =
        runTest {
            val quote = aQuotePostUi()
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns Result.success(quote)
            coEvery {
                postingRepository.createPost(
                    text = any(),
                    attachments = any(),
                    replyTo = any(),
                    langs = any(),
                    audience = any(),
                    quote = any(),
                )
            } returns Result.success(AtUri("at://did:plc:me/app.bsky.feed.post/new"))

            // No text, no attachments — the loaded quote is the only content.
            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)
            vm.handleEvent(ComposerEvent.Submit)

            coVerify(exactly = 1) {
                postingRepository.createPost(
                    text = "",
                    attachments = emptyList(),
                    replyTo = null,
                    langs = any(),
                    audience = any(),
                    quote = quote.ref,
                )
            }
        }

    @Test
    fun quoteMode_blocksSubmit_whileQuoteStillLoading() =
        runTest {
            val gate = CompletableDeferred<Result<QuotePostUi>>()
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)
            setComposerText(vm, "some text")

            // Quote still Loading — submit must be blocked even with text present.
            vm.handleEvent(ComposerEvent.Submit)

            coVerify(exactly = 0) { postingRepository.createPost(any(), any(), any()) }
        }

    @Test
    fun quoteMode_notQuotable_emitsShowError() =
        runTest {
            val gate = CompletableDeferred<Result<QuotePostUi>>()
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } coAnswers { gate.await() }

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)

            vm.effects.test {
                gate.complete(Result.success(aQuotePostUi(canViewerQuote = false)))
                assertEquals(ComposerEffect.ShowError(ComposerError.QuoteNotAllowed), awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun quoteMode_notQuotable_blocksSubmit() =
        runTest {
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns
                Result.success(aQuotePostUi(canViewerQuote = false))

            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)
            setComposerText(vm, "trying to quote a gated post")

            vm.handleEvent(ComposerEvent.Submit)

            coVerify(exactly = 0) { postingRepository.createPost(any(), any(), any()) }
        }

    @Test
    fun replyAndQuote_bothPassedToCreatePost() =
        runTest {
            val parent = aParentPostUi()
            val quote = aQuotePostUi()
            coEvery { parentFetchSource.fetchParent(AtUri(PARENT_URI)) } returns Result.success(parent)
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns Result.success(quote)
            coEvery {
                postingRepository.createPost(
                    text = any(),
                    attachments = any(),
                    replyTo = any(),
                    langs = any(),
                    audience = any(),
                    quote = any(),
                )
            } returns Result.success(AtUri("at://did:plc:me/app.bsky.feed.post/new"))

            val vm = newVm(replyToUri = PARENT_URI, quotePostUri = QUOTE_URI)
            setComposerText(vm, "reply and quote at once")
            vm.handleEvent(ComposerEvent.Submit)

            // reply (threading) and embed (quote) are independent — both reach createPost.
            coVerify(exactly = 1) {
                postingRepository.createPost(
                    text = "reply and quote at once",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parent.parentRef, root = parent.rootRef),
                    langs = any(),
                    audience = any(),
                    quote = quote.ref,
                )
            }
        }

    @Test
    fun pasteLink_resolves_attachesQuote_andStripsUrl() =
        runTest {
            val quote = aQuotePostUi()
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns Result.success(quote)

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "look $QUOTE_WEB_URL nice")

            assertEquals(QUOTE_URI, vm.uiState.value.quotePostUri)
            assertEquals(QuoteLoadStatus.Loaded(quote), vm.uiState.value.quotePostLoad)
            // URL stripped from the field on successful resolution.
            assertFalse(
                vm.textFieldState.text
                    .toString()
                    .contains("bsky.app"),
                "pasted URL should be stripped once the quote loads",
            )
        }

    @Test
    fun pasteLink_failedResolve_keepsUrl_andShowsFailedState() =
        runTest {
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns
                Result.failure(ComposerError.Network(RuntimeException("dns")))

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "look $QUOTE_WEB_URL nice")

            assertTrue(vm.uiState.value.quotePostLoad is QuoteLoadStatus.Failed)
            // URL left intact so the user doesn't lose it on a failed resolve.
            assertTrue(
                vm.textFieldState.text
                    .toString()
                    .contains(QUOTE_WEB_URL),
            )
        }

    @Test
    fun pasteLink_gatedPost_rejectsAttach_keepsUrl_andEmitsError() =
        runTest {
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns
                Result.success(aQuotePostUi(canViewerQuote = false))

            val vm = newVm(replyToUri = null)

            vm.effects.test {
                setComposerText(vm, "look $QUOTE_WEB_URL nice")
                assertEquals(ComposerEffect.ShowError(ComposerError.QuoteNotAllowed), awaitItem())
                cancelAndConsumeRemainingEvents()
            }
            // Reject: no quote attached, URL left as plain text.
            assertNull(vm.uiState.value.quotePostUri)
            assertNull(vm.uiState.value.quotePostLoad)
            assertTrue(
                vm.textFieldState.text
                    .toString()
                    .contains(QUOTE_WEB_URL),
            )
        }

    @Test
    fun pasteLink_ignoredWhileQuoteAlreadyAttached() =
        runTest {
            // A quote is already attached via the route; a pasted second link is
            // ignored (one quote max).
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } returns Result.success(aQuotePostUi())
            val vm = newVm(replyToUri = null, quotePostUri = QUOTE_URI)
            assertTrue(vm.uiState.value.quotePostLoad is QuoteLoadStatus.Loaded)

            setComposerText(vm, "another at://did:plc:carol/app.bsky.feed.post/other here")

            // Still the original quote — the second link was not attached.
            assertEquals(QUOTE_URI, vm.uiState.value.quotePostUri)
        }

    @Test
    fun pasteLink_afterRejectThenManualDelete_canBeReattached() =
        runTest {
            // First resolve is gated → rejected (URL kept, link remembered).
            // After the user deletes the URL and pastes it again, it must be
            // re-detected (attemptedQuoteLinks forgets links absent from the text).
            var calls = 0
            coEvery { quotePostFetcher.fetchQuote(AtUri(QUOTE_URI)) } coAnswers {
                if (calls++ == 0) {
                    Result.success(aQuotePostUi(canViewerQuote = false))
                } else {
                    Result.success(aQuotePostUi())
                }
            }

            val vm = newVm(replyToUri = null)
            setComposerText(vm, "look $QUOTE_WEB_URL nice")
            // Rejected: no quote, URL still in text, link remembered.
            assertNull(vm.uiState.value.quotePostUri)

            // User clears the field (deletes the URL) → set is pruned.
            setComposerText(vm, "")
            // Pastes it again → re-detected and now resolves.
            setComposerText(vm, "$QUOTE_WEB_URL again")

            assertEquals(QUOTE_URI, vm.uiState.value.quotePostUri)
            assertTrue(vm.uiState.value.quotePostLoad is QuoteLoadStatus.Loaded)
        }

    // ---- Paste-a-link external card (nubecita-gfli.2) ----

    @Test
    fun externalUrl_typed_fetchesAndShowsCard() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns aLinkPreview()
            val vm = newVm()

            setComposerText(vm, "look at $EXTERNAL_URL")

            assertEquals(ExternalLinkStatus.Loaded(aLinkPreview()), vm.uiState.value.externalLink)
        }

    @Test
    fun quoteLink_doesNotProduceExternalCard() =
        runTest {
            coEvery { quotePostFetcher.fetchQuote(any()) } returns Result.success(aQuotePostUi())
            val vm = newVm()

            setComposerText(vm, QUOTE_WEB_URL)

            // The bsky.app post link is a quote, never an external card.
            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
            coVerify(exactly = 0) { externalLinkMetadataRepository.fetch(any()) }
        }

    @Test
    fun imagesPresent_suppressExternalFetch() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att())))

            setComposerText(vm, "look at $EXTERNAL_URL")

            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
            coVerify(exactly = 0) { externalLinkMetadataRepository.fetch(any()) }
        }

    @Test
    fun addingImages_clearsLoadedCard() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns aLinkPreview()
            val vm = newVm()
            setComposerText(vm, EXTERNAL_URL)
            assertTrue(vm.uiState.value.externalLink is ExternalLinkStatus.Loaded)

            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att())))

            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
        }

    @Test
    fun removingImages_restoresCard() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns aLinkPreview()
            val vm = newVm()
            setComposerText(vm, EXTERNAL_URL) // card loaded
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att()))) // cleared + forgotten
            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)

            vm.handleEvent(ComposerEvent.RemoveAttachment(0)) // re-scan restores it

            assertTrue(vm.uiState.value.externalLink is ExternalLinkStatus.Loaded)
        }

    @Test
    fun manualDismiss_memoizes_noRepop() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns aLinkPreview()
            val vm = newVm()
            setComposerText(vm, EXTERNAL_URL)
            vm.handleEvent(ComposerEvent.RemoveExternalLink)
            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)

            // The same URL still in the text must NOT re-pop after a manual dismiss.
            setComposerText(vm, "$EXTERNAL_URL still here")

            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
        }

    @Test
    fun failedFetch_silentlyReturnsToIdle() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns null
            val vm = newVm()

            setComposerText(vm, EXTERNAL_URL)

            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
        }

    @Test
    fun lateFetchAfterImagesAttached_doesNotShowCard() =
        runTest {
            val gate = CompletableDeferred<LinkPreview?>()
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } coAnswers { gate.await() }
            val vm = newVm()

            setComposerText(vm, EXTERNAL_URL) // Loading; fetch suspended at the gate
            assertTrue(vm.uiState.value.externalLink is ExternalLinkStatus.Loading)

            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att()))) // cancels the fetch
            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)

            gate.complete(aLinkPreview()) // late — but the job was cancelled
            advanceUntilIdle()

            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
        }

    @Test
    fun gifPicked_setsPickedGif_andClearsALoadedLinkCard() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns aLinkPreview()
            val vm = newVm()
            setComposerText(vm, "look at $EXTERNAL_URL")
            assertTrue(vm.uiState.value.externalLink is ExternalLinkStatus.Loaded)

            vm.handleEvent(ComposerEvent.GifPicked(KlipyMediaUiFixtures.media(slug = "cat")))
            advanceUntilIdle()

            assertEquals(
                "cat",
                vm.uiState.value.pickedGif
                    ?.slug,
            )
            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)
        }

    @Test
    fun removeGif_reScansTextAndRestoresTheLinkCard() =
        runTest {
            coEvery { externalLinkMetadataRepository.fetch(EXTERNAL_URL) } returns aLinkPreview()
            val vm = newVm()
            setComposerText(vm, "look at $EXTERNAL_URL")
            vm.handleEvent(ComposerEvent.GifPicked(KlipyMediaUiFixtures.media(slug = "cat")))
            advanceUntilIdle()
            assertEquals(ExternalLinkStatus.Idle, vm.uiState.value.externalLink)

            vm.handleEvent(ComposerEvent.RemoveGif)
            advanceUntilIdle()

            assertNull(vm.uiState.value.pickedGif)
            assertTrue(vm.uiState.value.externalLink is ExternalLinkStatus.Loaded)
        }

    @Test
    fun gifPicked_isIgnored_whenPhotosAlreadyAttached() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att())))

            vm.handleEvent(ComposerEvent.GifPicked(KlipyMediaUiFixtures.media(slug = "cat")))
            advanceUntilIdle()

            assertNull(vm.uiState.value.pickedGif)
            assertEquals(1, vm.uiState.value.attachments.size)
        }

    @Test
    fun removeGif_clearsPickedGif() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.GifPicked(KlipyMediaUiFixtures.media(slug = "cat")))

            vm.handleEvent(ComposerEvent.RemoveGif)
            advanceUntilIdle()

            assertNull(vm.uiState.value.pickedGif)
        }

    @Test
    fun addAttachments_whileGifPicked_isIgnored() =
        runTest {
            val vm = newVm()
            vm.handleEvent(ComposerEvent.GifPicked(KlipyMediaUiFixtures.media(slug = "cat")))

            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att())))
            advanceUntilIdle()

            assertEquals(0, vm.uiState.value.attachments.size)
        }

    @Test
    fun submit_withPickedGif_postsStaticKlipyExternalEmbed() =
        runTest {
            val captured = mutableListOf<LinkPreview?>()
            coEvery {
                postingRepository.createPost(
                    text = any(),
                    attachments = any(),
                    replyTo = any(),
                    langs = any(),
                    audience = any(),
                    quote = any(),
                    external = captureNullable(captured),
                )
            } returns Result.success(AtUri("at://did:plc:me/app.bsky.feed.post/gif"))

            val vm = newVm()
            vm.handleEvent(
                ComposerEvent.GifPicked(
                    KlipyMediaUiFixtures.media(slug = "cat", embedWidth = 480, embedHeight = 360),
                ),
            )
            vm.handleEvent(ComposerEvent.Submit)
            advanceUntilIdle()

            assertEquals(ComposerSubmitStatus.Success, vm.uiState.value.submitStatus)
            val external = captured.last() ?: error("createPost external was null")
            assertTrue(external.uri.startsWith("https://static.klipy.com/ii/"))
            assertTrue(external.uri.contains("ww=480"))
            assertTrue(external.uri.contains("hh=360"))
            assertEquals("https://static.klipy.com/ii/preview/cat.webp", external.imageUrl)
        }

    private fun aLinkPreview(): LinkPreview =
        LinkPreview(
            uri = EXTERNAL_URL,
            title = "Example Title",
            description = "An example page.",
            imageUrl = "https://cardyb.bsky.app/v1/image?url=x",
        )

    private fun newVm(
        replyToUri: String? = null,
        quotePostUri: String? = null,
        deviceLocaleTag: String = "en-US",
    ): ComposerViewModel =
        ComposerViewModel(
            route = ComposerRoute(replyToUri = replyToUri, quotePostUri = quotePostUri),
            postingRepository = postingRepository,
            parentFetchSource = parentFetchSource,
            quotePostFetcher = quotePostFetcher,
            actorRepository = actorRepository,
            localeProvider = fixedLocaleProvider(deviceLocaleTag),
            postAudienceDefaultRepository = postAudienceDefaultRepository,
            externalLinkMetadataRepository = externalLinkMetadataRepository,
            reviewManager = mockk(relaxed = true),
        )

    private fun fixedLocaleProvider(tag: String): LocaleProvider =
        object : LocaleProvider {
            override fun primaryLanguageTag(): String = tag
        }

    /**
     * Mutates the VM's [textFieldState] and drives the Compose
     * snapshot system to flush the change to the VM's snapshotFlow
     * collector. In production the recomposer fires
     * `Snapshot.sendApplyNotifications()` on every frame; in unit
     * tests there is no frame loop, so we trigger it manually after
     * each mutation. `runCurrent()` then drives the test scheduler
     * past the suspending boundary inside the collector so the
     * grapheme/typeahead state assertions land before the next test
     * step.
     */
    private fun TestScope.setComposerText(
        vm: ComposerViewModel,
        text: String,
    ) {
        vm.textFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }

    private fun att(): ComposerAttachment = ComposerAttachment(uri = mockk(relaxed = true), mimeType = "image/jpeg")

    private fun aParentPostUi(canViewerReply: Boolean = true): ParentPostUi =
        ParentPostUi(
            parentRef = StrongRef(uri = AtUri(PARENT_URI), cid = Cid("bafparent")),
            rootRef = StrongRef(uri = AtUri(ROOT_URI), cid = Cid("bafroot")),
            authorHandle = "alice.test",
            authorDisplayName = "Alice",
            text = "parent post body",
            canViewerReply = canViewerReply,
        )

    private fun aQuotePostUi(canViewerQuote: Boolean = true): QuotePostUi =
        QuotePostUi(
            ref = StrongRef(uri = AtUri(QUOTE_URI), cid = Cid("bafquote")),
            authorHandle = "bob.test",
            authorDisplayName = "Bob",
            text = "quoted post body",
            canViewerQuote = canViewerQuote,
        )

    private companion object {
        const val PARENT_URI = "at://did:plc:alice/app.bsky.feed.post/parent"
        const val ROOT_URI = "at://did:plc:alice/app.bsky.feed.post/root"
        const val QUOTE_URI = "at://did:plc:bob/app.bsky.feed.post/quoted"

        // Web link whose authority+rkey resolve to QUOTE_URI via QuoteLinkDetector.
        const val QUOTE_WEB_URL = "https://bsky.app/profile/did:plc:bob/post/quoted"

        // A plain external (non-quote) URL for link-card detection.
        const val EXTERNAL_URL = "https://example.com/article"
    }
}
