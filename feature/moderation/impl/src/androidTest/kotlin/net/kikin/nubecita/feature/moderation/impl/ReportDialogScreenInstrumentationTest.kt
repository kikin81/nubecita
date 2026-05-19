package net.kikin.nubecita.feature.moderation.impl

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import net.kikin.nubecita.feature.moderation.impl.data.SubjectPreviewResolver
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Instrumentation coverage for the Report dialog's two critical user
 * journeys per spec scenarios:
 *
 * 1. **Spam fast-path → Success**. From the Subject step, tap Continue
 *    → tap the Spam card → land on Details with the legacy spam token
 *    pre-applied → tap Submit → the success card replaces the form.
 *
 * 2. **Back from Details collapses to SubReason** (with a non-Spam
 *    category so the SubReason step is reachable). System back-press
 *    while on Details fires the screen's BackHandler, which transitions
 *    `step` back to SubReason.
 *
 * Renders via `createAndroidComposeRule<ComponentActivity>()` — no Hilt
 * graph, no real network. The VM is constructed directly with inline
 * fakes (so this stays a single-file test fixture). Production wiring
 * is exercised by the unit tests in `src/test/` and the screen's own
 * preview gallery.
 */
@OptIn(ExperimentalTime::class)
class ReportDialogScreenInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun fullSpamFastPathFlow_landsOnSuccessCard() {
        val vm = newVm()

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ReportDialogScreen(viewModel = vm, onDismiss = {})
            }
        }

        // Subject step is in composition on first render.
        composeTestRule.onNodeWithTag(ReportDialogTestTags.SUBJECT_STEP).assertIsDisplayed()

        // Continue → Category step.
        composeTestRule.onNodeWithTag(ReportDialogTestTags.CONTINUE_CTA).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReportDialogTestTags.CATEGORY_STEP).assertIsDisplayed()

        // Tap the first category row (Spam — ordered first in CATEGORY_ORDER).
        // The category step renders 9 rows; the Spam fast-path collapses
        // the SubReason step and lands on Details with the legacy spam
        // token pre-applied + canSubmit = true. We click via the row's
        // own clickable Card — testTags on individual rows would over-
        // index; the step's tag plus index-into-children would be
        // brittle. Click via the rendered category label instead.
        composeTestRule
            .onNodeWithTag(ReportDialogTestTags.CATEGORY_STEP)
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.report_category_spam),
            ).performClick()
        composeTestRule.waitForIdle()

        // Details step is now in composition — fast-path skipped SubReason.
        composeTestRule.onNodeWithTag(ReportDialogTestTags.DETAILS_STEP).assertIsDisplayed()

        // Submit.
        composeTestRule.onNodeWithTag(ReportDialogTestTags.SUBMIT_CTA).performClick()

        // Wait for the repo coroutine to resolve and the success state to
        // commit. The repo is synchronous (returns Result.success
        // immediately), so the only delay is the launch-coroutine
        // scheduling round-trip. Generous timeout to absorb test-runner
        // variance on emulators.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule
                .onAllNodesWithTag(ReportDialogTestTags.SUCCESS_CARD)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(ReportDialogTestTags.SUCCESS_CARD).assertIsDisplayed()
    }

    @Test
    fun backFromDetails_returnsToSubReason() {
        val vm = newVm()

        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                ReportDialogScreen(viewModel = vm, onDismiss = {})
            }
        }

        // Drive the form to the Details step via a non-Spam category so
        // SubReason is reachable on Back.
        composeTestRule.onNodeWithTag(ReportDialogTestTags.CONTINUE_CTA).performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.report_category_violence),
            ).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReportDialogTestTags.SUBREASON_STEP).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(
                composeTestRule.activity.getString(R.string.report_reason_violence_threats),
            ).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReportDialogTestTags.DETAILS_STEP).assertIsDisplayed()

        // Fire system back — the screen's BackHandler intercepts (step
        // != Subject) and dispatches OnBackPressed → reducer transitions
        // Details → SubReason.
        Espresso.pressBack()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReportDialogTestTags.SUBREASON_STEP).assertIsDisplayed()
    }

    // ---------- VM factory + fakes ---------------------------------------

    private fun newVm(): ReportDialogViewModel {
        val subject =
            ReportSubject.Post(
                uri = "at://did:plc:abc/app.bsky.feed.post/3kxyz",
                cid = "bafyreitestcidtestcidtestcidtestcidtestcidtestcid",
            )
        return ReportDialogViewModel(
            route = Report(subject = subject),
            moderationRepository = AndroidTestFakeModerationRepository(),
            subjectPreviewResolver = AndroidTestFakeResolver(),
            clock =
                object : Clock {
                    override fun now(): Instant = Instant.parse("2026-05-19T12:00:00Z")
                },
        )
    }
}

private class AndroidTestFakeModerationRepository : ModerationRepository {
    override suspend fun reportPost(
        uri: String,
        cid: String,
        reasonToken: String,
        details: String?,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun reportAccount(
        did: String,
        reasonToken: String,
        details: String?,
    ): Result<Unit> = Result.success(Unit)
}

private class AndroidTestFakeResolver : SubjectPreviewResolver {
    override suspend fun resolvePost(uri: String): Result<SubjectPreview.Post> =
        Result.success(
            SubjectPreview.Post(
                authorHandle = "test.bsky.social",
                authorDisplayName = "Test",
                snippet = "Test snippet",
            ),
        )

    override suspend fun resolveAccount(did: String): Result<SubjectPreview.Account> = Result.success(SubjectPreview.Account(handle = "test.bsky.social", displayName = "Test"))
}
