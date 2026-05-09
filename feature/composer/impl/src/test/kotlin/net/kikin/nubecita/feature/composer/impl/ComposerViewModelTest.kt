package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.posting.ActorTypeaheadRepository
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyRefs
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.composer.impl.data.ParentFetchSource
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentPostUi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
 * + [ActorTypeaheadRepository] via mockk, assert state transitions
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

    // The typeahead repo is exercised by ComposerViewModelTypeaheadTest;
    // here we install a fake that returns empty so the snapshot
    // collector's typeahead pipeline does not interfere with the
    // assertions in this suite.
    private val actorTypeaheadRepository =
        object : ActorTypeaheadRepository {
            override suspend fun searchTypeahead(query: String): Result<List<net.kikin.nubecita.core.posting.ActorTypeaheadUi>> = Result.success(emptyList())
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
    fun addAttachments_capsAtFour() =
        runTest {
            val vm = newVm(replyToUri = null)

            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att(), att(), att())))
            assertEquals(3, vm.uiState.value.attachments.size)

            vm.handleEvent(ComposerEvent.AddAttachments(listOf(att(), att(), att())))
            assertEquals(4, vm.uiState.value.attachments.size)
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

    // ---------- harness ----------

    private fun newVm(
        replyToUri: String?,
        deviceLocaleTag: String = "en-US",
    ): ComposerViewModel =
        ComposerViewModel(
            route = ComposerRoute(replyToUri = replyToUri),
            postingRepository = postingRepository,
            parentFetchSource = parentFetchSource,
            actorTypeaheadRepository = actorTypeaheadRepository,
            localeProvider = fixedLocaleProvider(deviceLocaleTag),
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

    private fun aParentPostUi(): ParentPostUi =
        ParentPostUi(
            parentRef = StrongRef(uri = AtUri(PARENT_URI), cid = Cid("bafparent")),
            rootRef = StrongRef(uri = AtUri(ROOT_URI), cid = Cid("bafroot")),
            authorHandle = "alice.test",
            authorDisplayName = "Alice",
            text = "parent post body",
        )

    private companion object {
        const val PARENT_URI = "at://did:plc:alice/app.bsky.feed.post/parent"
        const val ROOT_URI = "at://did:plc:alice/app.bsky.feed.post/root"
    }
}
