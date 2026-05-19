package net.kikin.nubecita.feature.moderation.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import net.kikin.nubecita.feature.moderation.impl.data.SubjectPreviewResolver
import net.kikin.nubecita.feature.moderation.impl.data.internal.GraphemeText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Presenter for the Report dialog (`oftc.3.2`).
 *
 * Construction uses Hilt's assisted-injection bridge: the [Report]
 * NavKey flows from the entry-provider call site (`hiltViewModel<VM, Factory>(creationCallback = { it.create(route) })`)
 * into the constructor, same pattern as `PostDetailViewModel`. This
 * keeps the VM's state synchronously seeded against the navigated
 * subject — no SavedStateHandle decode step, no first-frame null —
 * which matters here because the Subject step needs `state.subject`
 * to know whether it's reporting a post or an account.
 *
 * The [Clock] is injected so unit tests can pin `SubmissionStatus.Success.sentAt`
 * to a fixed instant. Production binds the system clock.
 */
@OptIn(ExperimentalTime::class)
@HiltViewModel(assistedFactory = ReportDialogViewModel.Factory::class)
internal class ReportDialogViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: Report,
        private val moderationRepository: ModerationRepository,
        private val subjectPreviewResolver: SubjectPreviewResolver,
        private val clock: Clock,
    ) : MviViewModel<ReportDialogState, ReportDialogEvent, ReportDialogEffect>(
            ReportDialogState(subject = route.subject),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: Report): ReportDialogViewModel
        }

        init {
            resolveSubjectPreview()
        }

        override fun handleEvent(event: ReportDialogEvent) {
            when (event) {
                ReportDialogEvent.OnContinueClicked -> handleContinue()
                is ReportDialogEvent.OnCategorySelected -> handleCategorySelected(event.category)
                is ReportDialogEvent.OnReasonSelected -> handleReasonSelected(event.token)
                is ReportDialogEvent.OnDetailsChanged -> handleDetailsChanged(event.text)
                ReportDialogEvent.OnSubmitClicked -> handleSubmit()
                ReportDialogEvent.OnBackPressed -> handleBack()
                ReportDialogEvent.OnCancelClicked -> sendEffect(ReportDialogEffect.RequestDismiss)
            }
        }

        private fun handleContinue() {
            // Only Subject → Category here. Other steps advance via their
            // own typed events (category tap, reason tap, submit) so the
            // reducer never needs to guess what "continue" means.
            if (uiState.value.step != ReportDialogStep.Subject) return
            setState { copy(step = ReportDialogStep.Category).recomputeCanSubmit() }
        }

        // ---------- reducers --------------------------------------------------

        private fun handleCategorySelected(category: ReportCategory) {
            // Spam fast-path: only one reason in the list, so skip the
            // SubReason step entirely and land on Details with the legacy
            // spam token pre-applied. The Details step still renders —
            // the user can optionally add context — but Submit is enabled
            // immediately (legacy spam isn't in OTHER_REPORT_REASONS, so
            // detailsRequired stays false).
            val single = category.reasons.singleOrNull()
            if (single != null) {
                setState {
                    copy(
                        selectedCategory = category,
                        selectedReason = single,
                        detailsRequired = single in ReportReasons.OTHER_REPORT_REASONS,
                        step = ReportDialogStep.Details,
                    ).recomputeCanSubmit()
                }
            } else {
                setState {
                    copy(
                        selectedCategory = category,
                        // Discard any prior sub-reason / details when the user
                        // picks a new category — the prior selection wasn't a
                        // legal child of this category.
                        selectedReason = null,
                        details = "",
                        detailsGraphemeCount = 0,
                        detailsRequired = false,
                        step = ReportDialogStep.SubReason,
                    ).recomputeCanSubmit()
                }
            }
        }

        private fun handleReasonSelected(token: String) {
            val isOther = token in ReportReasons.OTHER_REPORT_REASONS
            setState {
                copy(
                    selectedReason = token,
                    detailsRequired = isOther,
                    step = ReportDialogStep.Details,
                ).recomputeCanSubmit()
            }
        }

        private fun handleDetailsChanged(text: String) {
            val truncated = GraphemeText.truncate(text, max = DETAILS_MAX_GRAPHEMES)
            val count = GraphemeText.count(truncated)
            setState {
                copy(
                    details = truncated,
                    detailsGraphemeCount = count,
                ).recomputeCanSubmit()
            }
        }

        private fun handleBack() {
            // Monotonic backward step transition, clearing the field set
            // during the abandoned step. Per the spec scenarios:
            // - Details → SubReason clears details (but keeps selectedReason
            //   preserved when this was a return from the user's own back
            //   press; spec scenario "Back from Details returns to SubReason
            //   and clears details" expects details cleared but reason kept).
            // - SubReason → Category clears selectedReason.
            // - Category → Subject clears selectedCategory.
            when (uiState.value.step) {
                ReportDialogStep.Details ->
                    setState {
                        copy(
                            details = "",
                            detailsGraphemeCount = 0,
                            detailsRequired = false,
                            step = ReportDialogStep.SubReason,
                        ).recomputeCanSubmit()
                    }
                ReportDialogStep.SubReason ->
                    setState {
                        copy(
                            selectedReason = null,
                            step = ReportDialogStep.Category,
                        ).recomputeCanSubmit()
                    }
                ReportDialogStep.Category ->
                    setState {
                        copy(
                            selectedCategory = null,
                            step = ReportDialogStep.Subject,
                        ).recomputeCanSubmit()
                    }
                ReportDialogStep.Subject -> {
                    // The BackHandler is disabled on Subject; this branch
                    // would only fire if a caller dispatched OnBackPressed
                    // explicitly. Treat it like Cancel — emit RequestDismiss
                    // so the entry provider pops the sub-route.
                    sendEffect(ReportDialogEffect.RequestDismiss)
                }
            }
        }

        // ---------- submission -----------------------------------------------

        private fun handleSubmit() {
            val current = uiState.value
            if (!current.canSubmit) return
            val reason = current.selectedReason ?: return
            val details = current.details.takeIf { it.isNotBlank() }
            setState {
                copy(submission = SubmissionStatus.Submitting).recomputeCanSubmit()
            }
            viewModelScope.launch {
                val result =
                    when (val subject = current.subject) {
                        is ReportSubject.Post ->
                            moderationRepository.reportPost(
                                uri = subject.uri,
                                cid = subject.cid,
                                reasonToken = reason,
                                details = details,
                            )
                        is ReportSubject.Account ->
                            moderationRepository.reportAccount(
                                did = subject.did,
                                reasonToken = reason,
                                details = details,
                            )
                    }
                result
                    .onSuccess {
                        setState {
                            copy(submission = SubmissionStatus.Success(sentAt = clock.now()))
                                .recomputeCanSubmit()
                        }
                        delay(SUCCESS_DISMISS_DELAY_MS)
                        sendEffect(ReportDialogEffect.RequestDismiss)
                    }.onFailure { throwable ->
                        val message =
                            throwable.localizedMessage
                                ?.takeIf { it.isNotBlank() }
                                ?: FALLBACK_ERROR_MESSAGE
                        setState {
                            copy(submission = SubmissionStatus.Failed(message = message))
                                .recomputeCanSubmit()
                        }
                    }
            }
        }

        // ---------- side-coroutines -----------------------------------------

        private fun resolveSubjectPreview() {
            viewModelScope.launch {
                val result =
                    when (val subject = route.subject) {
                        is ReportSubject.Post -> subjectPreviewResolver.resolvePost(subject.uri)
                        is ReportSubject.Account ->
                            subjectPreviewResolver.resolveAccount(subject.did)
                    }
                result.onSuccess { preview ->
                    setState { copy(subjectPreview = preview) }
                }
                // Failure is silent — `subjectPreview` stays null and the
                // Subject step renders the generic header card. The
                // resolver itself logs the throwable for triage.
            }
        }

        private companion object {
            /** UI-side cap on free-text details. The repo applies a 2000-grapheme floor before send. */
            const val DETAILS_MAX_GRAPHEMES = 300

            /** Auto-dismiss timer for the success card. See design Decision 5. */
            const val SUCCESS_DISMISS_DELAY_MS = 2_500L

            /** Fallback when the underlying exception has no localized message. */
            const val FALLBACK_ERROR_MESSAGE = "Couldn't submit report. Please try again."
        }
    }

/**
 * Recompute [ReportDialogState.canSubmit] from the flat fields per the
 * spec rule: a reason is chosen, details satisfy validation, and no
 * submission is in flight.
 *
 * Lives as an extension so reducers can chain `.recomputeCanSubmit()`
 * after every `copy(...)` without each branch having to duplicate the
 * rule. Centralizing the rule here is the single point of truth the
 * unit tests assert against.
 */
internal fun ReportDialogState.recomputeCanSubmit(): ReportDialogState {
    val reasonChosen = selectedReason != null
    val detailsOk = !detailsRequired || detailsGraphemeCount in 1..300
    val notSubmitting = submission !is SubmissionStatus.Submitting
    return copy(canSubmit = reasonChosen && detailsOk && notSubmitting)
}
