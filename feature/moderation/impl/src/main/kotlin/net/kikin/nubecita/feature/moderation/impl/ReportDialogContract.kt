package net.kikin.nubecita.feature.moderation.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import kotlin.time.Instant

/**
 * UI-side grapheme cap for the free-text details field. Shared between
 * the VM's truncation, the reducer's `canSubmit` range, and the screen's
 * `n/300` counter so the three never drift. The lexicon's own cap
 * (2000 graphemes, enforced inside `DefaultModerationRepository`) is a
 * separate floor — this is the smaller UI-level limit.
 */
internal const val REPORT_DETAILS_MAX_GRAPHEMES: Int = 300

/**
 * One frame's worth of UI state for the Report dialog.
 *
 * The four-step lifecycle (Subject → Category → SubReason → Details) is
 * mutually exclusive, so [step] is a sealed sum per CLAUDE.md's MVI
 * conventions. [submission] is also mutually exclusive across its four
 * variants (`Submitting` and `Failed` cannot coexist). Everything else
 * — [selectedCategory], [selectedReason], [details], [detailsRequired],
 * [canSubmit] — is an independent flat field; the reducer keeps them
 * coherent across step transitions.
 *
 * [canSubmit] is derived from the flat fields but persisted on state so
 * the Submit CTA reads a single boolean rather than re-running the
 * grapheme count + `Set.contains` on every recomposition. See
 * `ReportDialogState.recomputeCanSubmit` for the rule.
 */
@Immutable
internal data class ReportDialogState(
    val subject: ReportSubject,
    val subjectPreview: SubjectPreview? = null,
    val step: ReportDialogStep = ReportDialogStep.Subject,
    val selectedCategory: ReportCategory? = null,
    val selectedReason: String? = null,
    val details: String = "",
    val detailsGraphemeCount: Int = 0,
    val detailsRequired: Boolean = false,
    val submission: SubmissionStatus = SubmissionStatus.Idle,
    val canSubmit: Boolean = false,
) : UiState

/**
 * The mutually-exclusive lifecycle of the dialog form.
 *
 * Forward transitions move Subject → Category → SubReason → Details
 * (with the Spam fast-path collapsing SubReason; the Spam category has
 * exactly one reason, so selecting it lands directly on Details with
 * the reason pre-applied).
 *
 * Back transitions are monotonic the opposite direction, clearing the
 * field set during the abandoned step (see [ReportDialogEvent.OnBackPressed]).
 *
 * Not a [UiState] — that marker is reserved for the top-level screen
 * state ([ReportDialogState]). This is a sub-state sum carried inside
 * that screen state.
 */
internal sealed interface ReportDialogStep {
    @Immutable data object Subject : ReportDialogStep

    @Immutable data object Category : ReportDialogStep

    @Immutable data object SubReason : ReportDialogStep

    @Immutable data object Details : ReportDialogStep
}

/**
 * Network lifecycle for the report submission itself, orthogonal to the
 * form's [ReportDialogStep] axis.
 *
 * The `Failed.message` is the inline-banner copy displayed above the
 * Submit CTA; on retry, the form's other fields are preserved and the
 * submission re-runs against the same `subject` / `selectedReason` /
 * `details` (see design Decision 5).
 */
internal sealed interface SubmissionStatus {
    @Immutable data object Idle : SubmissionStatus

    @Immutable data object Submitting : SubmissionStatus

    @Immutable
    data class Success(
        val sentAt: Instant,
    ) : SubmissionStatus

    /**
     * Submission failed. [message] carries the underlying throwable's
     * `localizedMessage` when one was present; when null, the UI resolves
     * to the localized `R.string.report_dialog_error_submit_failed`
     * fallback. Keeping the resource indirection at the UI boundary
     * keeps the VM Android-resource-free.
     */
    @Immutable
    data class Failed(
        val message: String?,
    ) : SubmissionStatus
}

/**
 * Decorative metadata about the report's subject, rendered in the
 * Subject step's header card. Resolved off-thread by
 * [net.kikin.nubecita.feature.moderation.impl.data.SubjectPreviewResolver];
 * null while resolution is in flight (or after a resolution failure —
 * the dialog still functions without a preview).
 */
internal sealed interface SubjectPreview {
    /** Post preview — author handle + truncated text snippet. */
    @Immutable
    data class Post(
        val authorHandle: String,
        val authorDisplayName: String?,
        val snippet: String,
    ) : SubjectPreview

    /** Account preview — handle + optional display name. */
    @Immutable
    data class Account(
        val handle: String,
        val displayName: String?,
    ) : SubjectPreview
}

internal sealed interface ReportDialogEvent : UiEvent {
    /**
     * Continue CTA on the Subject step. Reducer transitions
     * [ReportDialogStep.Subject] → [ReportDialogStep.Category]. From
     * other steps the event is a no-op — the rest of the flow advances
     * via more specific events (category tap, reason tap, submit).
     */
    data object OnContinueClicked : ReportDialogEvent

    /** User tapped a category card on the Category step. */
    data class OnCategorySelected(
        val category: ReportCategory,
    ) : ReportDialogEvent

    /**
     * User tapped a sub-reason row on the SubReason step. The
     * `*Other`-suffixed tokens flip [ReportDialogState.detailsRequired]
     * to true; see [net.kikin.nubecita.feature.moderation.impl.ReportReasons.OTHER_REPORT_REASONS].
     */
    data class OnReasonSelected(
        val token: String,
    ) : ReportDialogEvent

    /**
     * Free-text input on the Details step. The reducer truncates to
     * 300 graphemes (the UI cap) before storing.
     */
    data class OnDetailsChanged(
        val text: String,
    ) : ReportDialogEvent

    /** Submit CTA tap. Gated by [ReportDialogState.canSubmit]. */
    data object OnSubmitClicked : ReportDialogEvent

    /**
     * Back-button press while [ReportDialogState.step] is not
     * [ReportDialogStep.Subject]. Step transitions Details → SubReason
     * → Category → Subject, clearing the field set during the abandoned
     * step.
     */
    data object OnBackPressed : ReportDialogEvent

    /**
     * Outside-sheet tap, drag-dismiss, or Back from the Subject step.
     * The VM emits [ReportDialogEffect.RequestDismiss] in response; the
     * entry provider then pops the [net.kikin.nubecita.feature.moderation.api.Report]
     * NavKey off [net.kikin.nubecita.core.common.navigation.LocalMainShellNavState].
     */
    data object OnCancelClicked : ReportDialogEvent
}

internal sealed interface ReportDialogEffect : UiEffect {
    /**
     * Pop the Report sub-route. Emitted by the VM on
     * [ReportDialogEvent.OnCancelClicked] AND from the post-success
     * auto-dismiss timer (the VM's `delay(2500)` after a successful
     * submission). The entry provider's effect collector translates
     * this to `LocalMainShellNavState.current.removeLast()`.
     */
    @Immutable data object RequestDismiss : ReportDialogEffect
}
