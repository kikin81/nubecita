package net.kikin.nubecita.feature.moderation.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * IDE / Android Studio preview fixtures for [ReportDialogContent].
 *
 * Driven through the stateless body with hand-built [ReportDialogState]
 * inputs so each preview captures one render path independently. The
 * matching screenshot tests in `src/screenshotTest/` re-use the same
 * fixture functions where possible to keep the preview gallery and the
 * baseline matrix in lockstep.
 */
@Preview(name = "subject — light", showBackground = true)
@Preview(name = "subject — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SubjectStepPreview() {
    NubecitaTheme {
        ReportDialogContent(state = previewSubjectState(), onEvent = {})
    }
}

@Preview(name = "subject (resolving) — light", showBackground = true)
@Composable
private fun SubjectStepResolvingPreview() {
    NubecitaTheme {
        ReportDialogContent(
            state = previewSubjectState(preview = null),
            onEvent = {},
        )
    }
}

@Preview(name = "category — light", showBackground = true)
@Preview(name = "category — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CategoryStepPreview() {
    NubecitaTheme {
        ReportDialogContent(state = previewCategoryState(), onEvent = {})
    }
}

@Preview(name = "subreason violence — light", showBackground = true)
@Preview(
    name = "subreason violence — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SubReasonViolencePreview() {
    NubecitaTheme {
        ReportDialogContent(
            state = previewSubReasonState(ReportCategory.Violence),
            onEvent = {},
        )
    }
}

@Preview(name = "details (OTHER required) — light", showBackground = true)
@Preview(
    name = "details (OTHER required) — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailsStepOtherPreview() {
    NubecitaTheme {
        ReportDialogContent(
            state = previewDetailsStateOther(),
            onEvent = {},
        )
    }
}

@Preview(name = "details (non-OTHER) — light", showBackground = true)
@Composable
private fun DetailsStepNonOtherPreview() {
    NubecitaTheme {
        ReportDialogContent(
            state = previewDetailsStateNonOther(),
            onEvent = {},
        )
    }
}

@Preview(name = "details — submitting", showBackground = true)
@Composable
private fun DetailsSubmittingPreview() {
    NubecitaTheme {
        ReportDialogContent(
            state = previewDetailsStateNonOther().copy(submission = SubmissionStatus.Submitting),
            onEvent = {},
        )
    }
}

@Preview(name = "details — failure banner — light", showBackground = true)
@Preview(
    name = "details — failure banner — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailsFailurePreview() {
    NubecitaTheme {
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

@Preview(name = "success — light", showBackground = true)
@Preview(name = "success — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SuccessCardPreview() {
    NubecitaTheme {
        ReportDialogContent(
            state =
                previewDetailsStateNonOther().copy(
                    submission = SubmissionStatus.Success(sentAt = PREVIEW_INSTANT),
                ),
            onEvent = {},
        )
    }
}

// ---------- fixture builders -----------------------------------------------

@OptIn(ExperimentalTime::class)
private val PREVIEW_INSTANT = Instant.parse("2026-05-19T12:00:00Z")

internal fun previewSubjectState(
    preview: SubjectPreview? =
        SubjectPreview.Post(
            authorHandle = "alice.bsky.social",
            authorDisplayName = "Alice",
            snippet = "Buy now! Limited offer — only 3 left at this price! 🚨🚨🚨",
        ),
): ReportDialogState =
    ReportDialogState(
        subject =
            ReportSubject.Post(
                uri = "at://did:plc:preview/app.bsky.feed.post/abc",
                cid = "bafyreipreviewpreviewpreviewpreviewpreviewpreviewp",
            ),
        subjectPreview = preview,
        step = ReportDialogStep.Subject,
    )

internal fun previewCategoryState(): ReportDialogState = previewSubjectState().copy(step = ReportDialogStep.Category)

internal fun previewSubReasonState(category: ReportCategory): ReportDialogState =
    previewCategoryState().copy(
        step = ReportDialogStep.SubReason,
        selectedCategory = category,
    )

internal fun previewDetailsStateOther(): ReportDialogState {
    val token = ReportReasons.REASON_RULE_OTHER
    return previewCategoryState()
        .copy(
            step = ReportDialogStep.Details,
            selectedCategory = ReportCategory.RuleViolation,
            selectedReason = token,
            detailsRequired = true,
            details = "They're circumventing a previous ban.",
            detailsGraphemeCount = "They're circumventing a previous ban.".length,
            canSubmit = true,
        )
}

internal fun previewDetailsStateNonOther(): ReportDialogState =
    previewCategoryState().copy(
        step = ReportDialogStep.Details,
        selectedCategory = ReportCategory.Spam,
        selectedReason = ReportReasons.REASON_LEGACY_SPAM,
        detailsRequired = false,
        details = "",
        detailsGraphemeCount = 0,
        canSubmit = true,
    )
