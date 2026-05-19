package net.kikin.nubecita.feature.moderation.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Screenshot baselines for [ReportDialogContent]'s render-state matrix:
 * each of the four steps (with the Spam fast-path captured separately),
 * plus the success card and the inline failure banner. Light + dark for
 * every render path.
 *
 * Driven through the stateless body with the same fixture state-builders
 * used by the IDE previews ([previewSubjectState], etc.). The baselines
 * MUST stay deterministic across machines — fixtures use a fixed
 * `Instant` and string literals only.
 *
 * Sections below are grouped by step (Subject → Category → SubReason →
 * Details → Success / Failure).
 */

@PreviewTest
@Preview(name = "subject-light", showBackground = true)
@Preview(name = "subject-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubjectStepScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(state = previewSubjectState(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "subject-resolving-light", showBackground = true)
@Preview(
    name = "subject-resolving-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SubjectStepResolvingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(state = previewSubjectState(preview = null), onEvent = {})
    }
}

// ---------- Category step --------------------------------------------------

@PreviewTest
@Preview(name = "category-light", showBackground = true)
@Preview(name = "category-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryStepScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(state = previewCategoryState(), onEvent = {})
    }
}

// ---------- SubReason step -------------------------------------------------

@PreviewTest
@Preview(name = "subreason-violence-light", showBackground = true)
@Preview(
    name = "subreason-violence-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SubReasonViolenceScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(
            state = previewSubReasonState(ReportCategory.Violence),
            onEvent = {},
        )
    }
}

// ---------- Details step ---------------------------------------------------

@PreviewTest
@Preview(name = "details-other-light", showBackground = true)
@Preview(name = "details-other-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailsStepOtherScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(state = previewDetailsStateOther(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "details-non-other-light", showBackground = true)
@Preview(
    name = "details-non-other-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailsStepNonOtherScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(state = previewDetailsStateNonOther(), onEvent = {})
    }
}

// Spam fast-path: tapping Spam on the Category step lands on Details
// with the legacy spam token applied (no SubReason step). This baseline
// captures that Details state — visually distinct from the OTHER-
// required variant (textarea is optional, Submit is enabled at empty).
@PreviewTest
@Preview(name = "details-spam-fastpath-light", showBackground = true)
@Preview(
    name = "details-spam-fastpath-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailsSpamFastpathScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(state = previewDetailsStateNonOther(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "details-submitting-light", showBackground = true)
@Preview(
    name = "details-submitting-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailsSubmittingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(
            state = previewDetailsStateNonOther().copy(submission = SubmissionStatus.Submitting),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "details-failure-light", showBackground = true)
@Preview(name = "details-failure-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DetailsFailureScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(
            state =
                previewDetailsStateNonOther().copy(
                    submission =
                        SubmissionStatus.Failed(message = "Couldn't submit report. Please try again."),
                ),
            onEvent = {},
        )
    }
}

// ---------- Success card ---------------------------------------------------

@OptIn(ExperimentalTime::class)
private val SCREENSHOT_SENT_AT = Instant.parse("2026-05-19T12:00:00Z")

@OptIn(ExperimentalTime::class)
@PreviewTest
@Preview(name = "success-light", showBackground = true)
@Preview(name = "success-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SuccessCardScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ReportDialogContent(
            state =
                previewDetailsStateNonOther().copy(
                    submission = SubmissionStatus.Success(sentAt = SCREENSHOT_SENT_AT),
                ),
            onEvent = {},
        )
    }
}
