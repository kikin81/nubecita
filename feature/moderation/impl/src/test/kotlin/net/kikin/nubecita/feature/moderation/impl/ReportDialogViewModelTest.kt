package net.kikin.nubecita.feature.moderation.impl

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import net.kikin.nubecita.feature.moderation.impl.data.SubjectPreviewResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Unit tests for [ReportDialogViewModel] covering the spec scenarios:
 *
 * - Initial state shape for Post vs Account subjects.
 * - Step transitions monotonic forward (via OnContinueClicked /
 *   OnCategorySelected / OnReasonSelected) and monotonic backward (via
 *   OnBackPressed), with field-clearing on each backward step.
 * - `*Other`-suffixed reasons flip `detailsRequired` to true.
 * - `canSubmit` derivation: false until reason is chosen, false when
 *   OTHER requires details but the field is blank, false during a
 *   submission in flight, true otherwise.
 * - Success path: repo returns Result.success → state.submission becomes
 *   Success → effects channel emits RequestDismiss after the 2.5s
 *   auto-timer.
 * - Failure path: repo returns Result.failure → state.submission becomes
 *   Failed with the throwable's localizedMessage (or the generic
 *   fallback), form fields preserved, retry re-invokes the repo with the
 *   same args.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
internal class ReportDialogViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // ---------- initial state ------------------------------------------------

    @Test
    fun `initial state for a post report seeds Subject step and starts preview resolution`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newVm(
                    subject = POST_SUBJECT,
                    resolver = FakeResolver(postResult = Result.success(SAMPLE_POST_PREVIEW)),
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(POST_SUBJECT, state.subject)
            assertEquals(ReportDialogStep.Subject, state.step)
            assertNull(state.selectedCategory)
            assertNull(state.selectedReason)
            assertEquals("", state.details)
            assertFalse(state.detailsRequired)
            assertEquals(SubmissionStatus.Idle, state.submission)
            assertEquals(SAMPLE_POST_PREVIEW, state.subjectPreview)
            assertFalse(state.canSubmit)
        }

    @Test
    fun `initial state for an account report seeds Subject step with account preview`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newVm(
                    subject = ACCOUNT_SUBJECT,
                    resolver = FakeResolver(accountResult = Result.success(SAMPLE_ACCOUNT_PREVIEW)),
                )
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(ACCOUNT_SUBJECT, state.subject)
            assertEquals(ReportDialogStep.Subject, state.step)
            assertEquals(SAMPLE_ACCOUNT_PREVIEW, state.subjectPreview)
        }

    @Test
    fun `subject preview resolution failure leaves subjectPreview null`() =
        runTest(mainDispatcher.dispatcher) {
            val vm =
                newVm(
                    subject = POST_SUBJECT,
                    resolver =
                        FakeResolver(
                            postResult = Result.failure(IOException("network down")),
                        ),
                )
            advanceUntilIdle()

            assertNull(vm.uiState.value.subjectPreview)
            assertEquals(ReportDialogStep.Subject, vm.uiState.value.step)
        }

    // ---------- forward transitions ----------------------------------------

    @Test
    fun `OnContinueClicked from Subject transitions to Category`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)

            assertEquals(ReportDialogStep.Category, vm.uiState.value.step)
        }

    @Test
    fun `OnCategorySelected non-spam transitions to SubReason`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Violence))

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.SubReason, state.step)
            assertEquals(ReportCategory.Violence, state.selectedCategory)
            assertNull(state.selectedReason)
        }

    @Test
    fun `OnCategorySelected Spam fast-paths to Details with legacy spam token applied`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Spam))

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.Details, state.step)
            assertEquals(ReportCategory.Spam, state.selectedCategory)
            assertEquals(ReportReasons.REASON_LEGACY_SPAM, state.selectedReason)
            assertFalse(state.detailsRequired)
            assertTrue(state.canSubmit)
        }

    @Test
    fun `Selecting an OTHER reason flips detailsRequired and lands on Details`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Violence))
            vm.handleEvent(ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_OTHER))

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.Details, state.step)
            assertEquals(ReportReasons.REASON_VIOLENCE_OTHER, state.selectedReason)
            assertTrue(state.detailsRequired)
            assertFalse(state.canSubmit) // OTHER + blank details = not submittable
        }

    @Test
    fun `Selecting a non-OTHER reason keeps detailsRequired false`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Violence))
            vm.handleEvent(
                ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT),
            )

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.Details, state.step)
            assertEquals(ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT, state.selectedReason)
            assertFalse(state.detailsRequired)
            assertTrue(state.canSubmit) // non-OTHER allows empty details
        }

    // ---------- backward transitions ---------------------------------------

    @Test
    fun `OnBackPressed from Details clears details and returns to SubReason`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.RuleViolation))
            vm.handleEvent(ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_RULE_OTHER))
            vm.handleEvent(ReportDialogEvent.OnDetailsChanged("they're banned"))
            vm.handleEvent(ReportDialogEvent.OnBackPressed)

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.SubReason, state.step)
            assertEquals(ReportReasons.REASON_RULE_OTHER, state.selectedReason) // preserved
            assertEquals("", state.details)
            assertEquals(0, state.detailsGraphemeCount)
            assertFalse(state.detailsRequired)
        }

    @Test
    fun `OnBackPressed from SubReason clears selectedReason and returns to Category`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Violence))
            vm.handleEvent(
                ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT),
            )
            // Now on Details — back once to SubReason, back again to Category.
            vm.handleEvent(ReportDialogEvent.OnBackPressed)
            vm.handleEvent(ReportDialogEvent.OnBackPressed)

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.Category, state.step)
            assertEquals(ReportCategory.Violence, state.selectedCategory) // preserved
            assertNull(state.selectedReason)
        }

    @Test
    fun `OnBackPressed from Category clears selectedCategory and returns to Subject`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Misleading))
            // Now on SubReason — back twice to Category, then once more to Subject.
            vm.handleEvent(ReportDialogEvent.OnBackPressed)
            vm.handleEvent(ReportDialogEvent.OnBackPressed)

            val state = vm.uiState.value
            assertEquals(ReportDialogStep.Subject, state.step)
            assertNull(state.selectedCategory)
        }

    @Test
    fun `OnBackPressed from Subject emits RequestDismiss`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.effects.test {
                vm.handleEvent(ReportDialogEvent.OnBackPressed)
                assertEquals(ReportDialogEffect.RequestDismiss, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------- canSubmit derivation ---------------------------------------

    @Test
    fun `canSubmit is false until a reason is selected`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            assertFalse(vm.uiState.value.canSubmit)
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            assertFalse(vm.uiState.value.canSubmit)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Violence))
            assertFalse(vm.uiState.value.canSubmit)
        }

    @Test
    fun `canSubmit is false when OTHER reason has empty details`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Harassment))
            vm.handleEvent(ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_HARASSMENT_OTHER))

            assertFalse(vm.uiState.value.canSubmit)

            // Typing 1 grapheme should flip canSubmit to true.
            vm.handleEvent(ReportDialogEvent.OnDetailsChanged("x"))
            assertTrue(vm.uiState.value.canSubmit)
        }

    @Test
    fun `canSubmit is false while a submission is in flight`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeModerationRepository(reportPostResult = { Result.success(Unit) })
            val vm = newVm(repository = repo)
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Spam))
            // Fast-path: already on Details with canSubmit=true.
            assertTrue(vm.uiState.value.canSubmit)

            vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
            // Submitting in progress — canSubmit should be false until the
            // coroutine resolves. Repo is queued to resolve on advanceUntilIdle.
            assertFalse(vm.uiState.value.canSubmit)
            assertEquals(SubmissionStatus.Submitting, vm.uiState.value.submission)
        }

    // ---------- successful submission -------------------------------------

    @Test
    fun `successful submission transitions to Success and emits RequestDismiss after the timer`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeModerationRepository(reportPostResult = { Result.success(Unit) })
            val sentAt = Instant.parse("2026-05-19T12:00:00Z")
            val vm = newVm(repository = repo, clock = fixedClock(sentAt))

            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Spam))
            vm.effects.test {
                vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
                advanceTimeBy(100)
                assertEquals(
                    SubmissionStatus.Success(sentAt = sentAt),
                    vm.uiState.value.submission,
                )
                advanceTimeBy(2_500)
                assertEquals(ReportDialogEffect.RequestDismiss, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `successful account submission routes through reportAccount`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeModerationRepository(reportAccountResult = { Result.success(Unit) })
            val vm = newVm(subject = ACCOUNT_SUBJECT, repository = repo)

            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Misleading))
            vm.handleEvent(
                ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_MISLEADING_IMPERSONATION),
            )
            vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
            advanceUntilIdle()

            assertEquals(0, repo.postInvocations.size)
            assertEquals(1, repo.accountInvocations.size)
            assertEquals(ACCOUNT_SUBJECT.did, repo.accountInvocations[0].did)
            assertEquals(
                ReportReasons.REASON_MISLEADING_IMPERSONATION,
                repo.accountInvocations[0].reasonToken,
            )
        }

    // ---------- failure + retry --------------------------------------------

    @Test
    fun `failed submission preserves form state and surfaces the error message`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeModerationRepository(
                    reportPostResult = { Result.failure(IOException("network down")) },
                )
            val vm = newVm(repository = repo)
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Violence))
            vm.handleEvent(
                ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT),
            )
            vm.handleEvent(ReportDialogEvent.OnDetailsChanged("context"))
            vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.submission is SubmissionStatus.Failed)
            assertEquals(
                "network down",
                (state.submission as SubmissionStatus.Failed).message,
            )
            assertEquals(
                ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT,
                state.selectedReason,
            )
            assertEquals("context", state.details)
            assertEquals(ReportDialogStep.Details, state.step)
        }

    @Test
    fun `failed submission with no message leaves message null for UI fallback`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeModerationRepository(
                    reportPostResult = { Result.failure(RuntimeException()) },
                )
            val vm = newVm(repository = repo)
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(ReportDialogEvent.OnCategorySelected(ReportCategory.Spam))
            vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
            advanceUntilIdle()

            // Throwable with no `localizedMessage` → `Failed.message` is
            // null. The UI's `FailureBanner` resolves null to the
            // localized `R.string.report_dialog_error_submit_failed`
            // fallback; the VM stays Android-resource-free.
            val failed = vm.uiState.value.submission as SubmissionStatus.Failed
            assertNull(failed.message)
        }

    @Test
    fun `retry from Failed re-invokes the repo with identical args`() =
        runTest(mainDispatcher.dispatcher) {
            // First call fails, second succeeds — same args expected on both invocations.
            val attempts: ArrayDeque<Result<Unit>> = ArrayDeque()
            attempts.addLast(Result.failure(IOException("transient")))
            attempts.addLast(Result.success(Unit))
            val repo =
                FakeModerationRepository(
                    reportPostResult = { attempts.removeFirst() },
                )
            val vm = newVm(repository = repo)
            vm.handleEvent(ReportDialogEvent.OnContinueClicked)
            vm.handleEvent(
                ReportDialogEvent.OnCategorySelected(ReportCategory.RuleViolation),
            )
            vm.handleEvent(
                ReportDialogEvent.OnReasonSelected(ReportReasons.REASON_RULE_BAN_EVASION),
            )
            vm.handleEvent(ReportDialogEvent.OnDetailsChanged("evidence"))
            vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.submission is SubmissionStatus.Failed)

            // Retry — same form state, same repo args.
            vm.handleEvent(ReportDialogEvent.OnSubmitClicked)
            advanceUntilIdle()

            assertEquals(2, repo.postInvocations.size)
            val first = repo.postInvocations[0]
            val second = repo.postInvocations[1]
            assertEquals(first.uri, second.uri)
            assertEquals(first.cid, second.cid)
            assertEquals(first.reasonToken, second.reasonToken)
            assertEquals(first.details, second.details)
            assertTrue(vm.uiState.value.submission is SubmissionStatus.Success)
        }

    @Test
    fun `OnCancelClicked emits RequestDismiss`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm()
            vm.effects.test {
                vm.handleEvent(ReportDialogEvent.OnCancelClicked)
                assertEquals(ReportDialogEffect.RequestDismiss, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    // ---------- factories + fakes -----------------------------------------

    private fun newVm(
        subject: ReportSubject = POST_SUBJECT,
        repository: ModerationRepository = FakeModerationRepository(),
        resolver: SubjectPreviewResolver = FakeResolver(),
        clock: Clock = fixedClock(Instant.parse("2026-05-19T12:00:00Z")),
    ): ReportDialogViewModel =
        ReportDialogViewModel(
            route = Report(subject = subject),
            moderationRepository = repository,
            subjectPreviewResolver = resolver,
            clock = clock,
        )

    private fun fixedClock(instant: Instant): Clock =
        object : Clock {
            override fun now(): Instant = instant
        }

    private companion object {
        val POST_SUBJECT =
            ReportSubject.Post(
                uri = "at://did:plc:abc/app.bsky.feed.post/3kxyz",
                cid = "bafyreitestcidtestcidtestcidtestcidtestcidtestcid",
            )

        val ACCOUNT_SUBJECT = ReportSubject.Account(did = "did:plc:xyz")

        val SAMPLE_POST_PREVIEW =
            SubjectPreview.Post(
                authorHandle = "alice.bsky.social",
                authorDisplayName = "Alice",
                snippet = "Buy now! Limited offer.",
            )

        val SAMPLE_ACCOUNT_PREVIEW =
            SubjectPreview.Account(handle = "spammer.example.com", displayName = "Spammer")
    }
}

// ---------- fakes -----------------------------------------------------------

internal class FakeModerationRepository(
    private val reportPostResult: () -> Result<Unit> = { Result.success(Unit) },
    private val reportAccountResult: () -> Result<Unit> = { Result.success(Unit) },
) : ModerationRepository {
    data class PostInvocation(
        val uri: String,
        val cid: String,
        val reasonToken: String,
        val details: String?,
    )

    data class AccountInvocation(
        val did: String,
        val reasonToken: String,
        val details: String?,
    )

    val postInvocations: MutableList<PostInvocation> = mutableListOf()
    val accountInvocations: MutableList<AccountInvocation> = mutableListOf()

    override suspend fun reportPost(
        uri: String,
        cid: String,
        reasonToken: String,
        details: String?,
    ): Result<Unit> {
        postInvocations.add(PostInvocation(uri, cid, reasonToken, details))
        return reportPostResult()
    }

    override suspend fun reportAccount(
        did: String,
        reasonToken: String,
        details: String?,
    ): Result<Unit> {
        accountInvocations.add(AccountInvocation(did, reasonToken, details))
        return reportAccountResult()
    }
}

internal class FakeResolver(
    private val postResult: Result<SubjectPreview.Post> =
        Result.success(
            SubjectPreview.Post(
                authorHandle = "fake.bsky.social",
                authorDisplayName = "Fake",
                snippet = "Sample snippet",
            ),
        ),
    private val accountResult: Result<SubjectPreview.Account> =
        Result.success(SubjectPreview.Account(handle = "fake.bsky.social", displayName = "Fake")),
) : SubjectPreviewResolver {
    override suspend fun resolvePost(uri: String): Result<SubjectPreview.Post> = postResult

    override suspend fun resolveAccount(did: String): Result<SubjectPreview.Account> = accountResult
}
