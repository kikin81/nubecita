package net.kikin.nubecita.feature.moderation.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.moderation.api.ReportSubject

/**
 * Test-tag identifiers exposed for instrumentation tests in this and
 * downstream feature modules (feed / profile-side wiring will assert
 * the Subject step's tag is present after the overflow-tap nav push).
 *
 * Public on purpose: `:feature:feed:impl` and `:feature:profile:impl`
 * androidTest sources will reference these constants directly when
 * PR 3 / PR 4 land their overflow-tap → dialog instrumentation tests.
 * Strings stay as `const val` so the cross-module reference is a
 * Java-bytecode constant rather than a method call.
 */
object ReportDialogTestTags {
    const val ROOT: String = "report-dialog-root"
    const val SUBJECT_STEP: String = "report-dialog-subject"
    const val CATEGORY_STEP: String = "report-dialog-category"
    const val SUBREASON_STEP: String = "report-dialog-subreason"
    const val DETAILS_STEP: String = "report-dialog-details"
    const val SUCCESS_CARD: String = "report-dialog-success"
    const val FAILURE_BANNER: String = "report-dialog-failure"
    const val SUBMIT_CTA: String = "report-dialog-submit"
    const val CONTINUE_CTA: String = "report-dialog-continue"
}

/**
 * Stateful Report dialog screen.
 *
 * Wraps [ReportDialogContent] with the lifecycle wiring (state
 * collection, single-collector effect channel, BackHandler). Sheet
 * presentation lives in `ModerationNavigationModule` — this composable
 * renders the dialog's body content so tests can drive the form
 * without standing up a `ModalBottomSheet`'s animation harness.
 *
 * [onDismiss] is invoked when the VM emits
 * [ReportDialogEffect.RequestDismiss] (cancel tap, drag-dismiss, or the
 * post-success auto-timer). The entry provider passes
 * `mainShellNavState.removeLast()`.
 */
@Composable
internal fun ReportDialogScreen(
    viewModel: ReportDialogViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Keep the dismiss callback identity stable across recompositions
    // so the effects collector below — which runs in a `LaunchedEffect(Unit)`
    // and would otherwise capture a stale reference — always invokes the
    // live `onDismiss`. Matches the rememberUpdatedState pattern used
    // throughout the screen layer (see `PostDetailScreen`).
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ReportDialogEffect.RequestDismiss -> currentOnDismiss()
            }
        }
    }

    // Back collapses through the form steps. Disabled on Subject (so the
    // press falls through to ModalBottomSheet's onDismissRequest) and
    // disabled during Submitting / Success (so an accidental Back mid-
    // submit or mid-success-timer doesn't lose user progress or interrupt
    // the auto-dismiss).
    val canStepBack =
        state.step != ReportDialogStep.Subject &&
            state.submission !is SubmissionStatus.Submitting &&
            state.submission !is SubmissionStatus.Success
    BackHandler(enabled = canStepBack) { viewModel.handleEvent(ReportDialogEvent.OnBackPressed) }

    ReportDialogContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}

/**
 * Stateless dialog body — drives every render path off the supplied
 * [ReportDialogState] and routes user input through [onEvent]. Used by
 * the stateful [ReportDialogScreen], previews, and screenshot tests.
 *
 * Render hierarchy:
 * - When `submission is Success` → [SuccessCard] replaces the form.
 * - Otherwise the appropriate step Composable renders, with the
 *   `Submitting` overlay (CTA disabled + the form left otherwise intact)
 *   and the `Failed` inline banner (above the Submit CTA on the Details
 *   step) layered on top of the step content as needed.
 */
@Composable
internal fun ReportDialogContent(
    state: ReportDialogState,
    onEvent: (ReportDialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .testTag(ReportDialogTestTags.ROOT)
                .fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DialogHeader(
                titleRes = headerTitleResForStep(state),
                showClose = state.submission !is SubmissionStatus.Success,
                onClose = { onEvent(ReportDialogEvent.OnCancelClicked) },
            )
            when (state.submission) {
                is SubmissionStatus.Success -> SuccessCard()
                else -> StepBody(state = state, onEvent = onEvent)
            }
        }
    }
}

// Pure function — not `@Composable`. Avoids the implicit `Composer`
// parameter every recomposition would otherwise pay just to look up
// a string-resource id. The caller (`ReportDialogContent`) reads
// `state.step` / `state.submission` directly; this helper is a closed
// `when` over those two axes.
private fun headerTitleResForStep(state: ReportDialogState): Int =
    when (state.submission) {
        is SubmissionStatus.Success -> R.string.report_dialog_title_success
        else ->
            when (state.step) {
                ReportDialogStep.Subject -> R.string.report_dialog_title_subject
                ReportDialogStep.Category -> R.string.report_dialog_title_category
                ReportDialogStep.SubReason -> R.string.report_dialog_title_subreason
                ReportDialogStep.Details -> R.string.report_dialog_title_details
            }
    }

@Composable
private fun DialogHeader(
    titleRes: Int,
    showClose: Boolean,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        if (showClose) {
            IconButton(onClick = onClose) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.report_dialog_close_content_description),
                )
            }
        }
    }
}

@Composable
private fun StepBody(
    state: ReportDialogState,
    onEvent: (ReportDialogEvent) -> Unit,
) {
    when (state.step) {
        ReportDialogStep.Subject ->
            SubjectStep(
                subject = state.subject,
                preview = state.subjectPreview,
                onContinue = { onEvent(ReportDialogEvent.OnContinueClicked) },
            )
        ReportDialogStep.Category ->
            CategoryStep(onCategoryClick = { onEvent(ReportDialogEvent.OnCategorySelected(it)) })
        ReportDialogStep.SubReason ->
            SubReasonStep(
                category = state.selectedCategory,
                onReasonClick = { onEvent(ReportDialogEvent.OnReasonSelected(it)) },
            )
        ReportDialogStep.Details ->
            DetailsStep(
                state = state,
                onDetailsChange = { onEvent(ReportDialogEvent.OnDetailsChanged(it)) },
                onSubmit = { onEvent(ReportDialogEvent.OnSubmitClicked) },
            )
    }
}

// ---------- Subject step ----------------------------------------------------

@Composable
private fun SubjectStep(
    subject: ReportSubject,
    preview: SubjectPreview?,
    onContinue: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ReportDialogTestTags.SUBJECT_STEP),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SubjectPreviewCard(subject = subject, preview = preview)
        Button(
            onClick = onContinue,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ReportDialogTestTags.CONTINUE_CTA),
        ) {
            Text(stringResource(R.string.report_dialog_continue_cta))
        }
    }
}

@Composable
private fun SubjectPreviewCard(
    subject: ReportSubject,
    preview: SubjectPreview?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                preview == null -> SubjectPreviewSkeleton(subject)
                preview is SubjectPreview.Post ->
                    PostPreviewBody(preview)
                preview is SubjectPreview.Account ->
                    AccountPreviewBody(preview)
            }
        }
    }
}

@Composable
private fun SubjectPreviewSkeleton(subject: ReportSubject) {
    // Generic header copy while the resolver coroutine is in flight (or
    // after it fails). The Subject step doesn't gate on the preview —
    // the user can still proceed.
    val fallback =
        when (subject) {
            is ReportSubject.Post -> stringResource(R.string.report_dialog_subject_fallback_post)
            is ReportSubject.Account -> stringResource(R.string.report_dialog_subject_fallback_account)
        }
    Text(text = fallback, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ColumnScope.PostPreviewBody(preview: SubjectPreview.Post) {
    Text(
        text = "@${preview.authorHandle}",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
    )
    if (!preview.authorDisplayName.isNullOrBlank()) {
        Text(text = preview.authorDisplayName, style = MaterialTheme.typography.bodySmall)
    }
    if (preview.snippet.isNotBlank()) {
        Text(
            text = preview.snippet,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColumnScope.AccountPreviewBody(preview: SubjectPreview.Account) {
    if (!preview.displayName.isNullOrBlank()) {
        Text(
            text = preview.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Text(text = "@${preview.handle}", style = MaterialTheme.typography.bodyMedium)
}

// ---------- Category step ---------------------------------------------------

@Composable
private fun CategoryStep(onCategoryClick: (ReportCategory) -> Unit) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .testTag(ReportDialogTestTags.CATEGORY_STEP),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(
            items = CATEGORY_ORDER,
            // The fully-qualified class name of each `data object` variant
            // is the stable, unique key — guards against positional re-
            // keying if CATEGORY_ORDER is ever re-ordered in a future PR.
            key = { it::class.qualifiedName ?: it.toString() },
        ) { category ->
            CategoryRow(
                category = category,
                onClick = { onCategoryClick(category) },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: ReportCategory,
    onClick: () -> Unit,
) {
    val labelRes = labelResForCategory(category)
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Error,
                contentDescription = null,
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ---------- SubReason step --------------------------------------------------

@Composable
private fun SubReasonStep(
    category: ReportCategory?,
    onReasonClick: (String) -> Unit,
) {
    // category should never be null on this step — the Category step
    // sets it before transitioning. Guard defensively so a malformed
    // state doesn't crash production; render an empty list instead.
    val reasons = category?.reasons.orEmpty()
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .testTag(ReportDialogTestTags.SUBREASON_STEP),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // Token strings are unique per reason — use them directly as the
        // stable key so the row identity survives Step → Back → Step
        // re-entries without re-running each row's enter animation.
        items(items = reasons, key = { it }) { token ->
            SubReasonRow(token = token, onClick = { onReasonClick(token) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun SubReasonRow(
    token: String,
    onClick: () -> Unit,
) {
    val labelRes = labelResForReasonToken(token)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

// ---------- Details step ----------------------------------------------------

@Composable
private fun DetailsStep(
    state: ReportDialogState,
    onDetailsChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ReportDialogTestTags.DETAILS_STEP),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.report_dialog_details_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DetailsField(
            details = state.details,
            count = state.detailsGraphemeCount,
            required = state.detailsRequired,
            onDetailsChange = onDetailsChange,
        )
        if (state.submission is SubmissionStatus.Failed) {
            FailureBanner(message = state.submission.message)
        }
        Button(
            onClick = onSubmit,
            enabled = state.canSubmit,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(ReportDialogTestTags.SUBMIT_CTA),
        ) {
            if (state.submission is SubmissionStatus.Submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.report_dialog_submit_cta))
            }
        }
    }
}

@Composable
private fun DetailsField(
    details: String,
    count: Int,
    required: Boolean,
    onDetailsChange: (String) -> Unit,
) {
    val placeholderRes =
        if (required) {
            R.string.report_dialog_details_placeholder_required
        } else {
            R.string.report_dialog_details_placeholder_optional
        }
    OutlinedTextField(
        value = details,
        onValueChange = onDetailsChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
        placeholder = { Text(stringResource(placeholderRes)) },
        isError = required && details.isBlank(),
        supportingText = {
            Text(
                text =
                    stringResource(
                        R.string.report_dialog_details_counter,
                        count,
                        REPORT_DETAILS_MAX_GRAPHEMES,
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        },
    )
}

@Composable
private fun FailureBanner(message: String) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ReportDialogTestTags.FAILURE_BANNER),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Error,
                contentDescription = null,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

// ---------- Success card ---------------------------------------------------

@Composable
private fun SuccessCard() {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(ReportDialogTestTags.SUCCESS_CARD),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Check,
                contentDescription = null,
                filled = true,
            )
            Text(
                text = stringResource(R.string.report_dialog_success_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.report_dialog_success_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- string-resource lookup helpers ----------------------------------
//
// These are plain functions, not `@Composable`. Each returns an `Int`
// resource id from a closed `when` over a sealed sum (or a string
// constant). Marking them `@Composable` would pay the implicit-Composer
// cost without any benefit — they read no composition locals and call
// nothing composable themselves.

private fun labelResForCategory(category: ReportCategory): Int =
    when (category) {
        ReportCategory.Spam -> R.string.report_category_spam
        ReportCategory.Sexual -> R.string.report_category_sexual
        ReportCategory.Violence -> R.string.report_category_violence
        ReportCategory.ChildSafety -> R.string.report_category_child_safety
        ReportCategory.Harassment -> R.string.report_category_harassment
        ReportCategory.Misleading -> R.string.report_category_misleading
        ReportCategory.RuleViolation -> R.string.report_category_rule_violation
        ReportCategory.SelfHarm -> R.string.report_category_self_harm
        ReportCategory.Other -> R.string.report_category_other
    }

private fun labelResForReasonToken(token: String): Int =
    when (token) {
        ReportReasons.REASON_LEGACY_SPAM -> R.string.report_reason_legacy_spam
        ReportReasons.REASON_VIOLENCE_ANIMAL -> R.string.report_reason_violence_animal
        ReportReasons.REASON_VIOLENCE_THREATS -> R.string.report_reason_violence_threats
        ReportReasons.REASON_VIOLENCE_GRAPHIC_CONTENT -> R.string.report_reason_violence_graphic
        ReportReasons.REASON_VIOLENCE_GLORIFICATION -> R.string.report_reason_violence_glorification
        ReportReasons.REASON_VIOLENCE_EXTREMIST_CONTENT -> R.string.report_reason_violence_extremist
        ReportReasons.REASON_VIOLENCE_TRAFFICKING -> R.string.report_reason_violence_trafficking
        ReportReasons.REASON_VIOLENCE_OTHER -> R.string.report_reason_violence_other
        ReportReasons.REASON_SEXUAL_ABUSE_CONTENT -> R.string.report_reason_sexual_abuse
        ReportReasons.REASON_SEXUAL_NCII -> R.string.report_reason_sexual_ncii
        ReportReasons.REASON_SEXUAL_DEEPFAKE -> R.string.report_reason_sexual_deepfake
        ReportReasons.REASON_SEXUAL_ANIMAL -> R.string.report_reason_sexual_animal
        ReportReasons.REASON_SEXUAL_UNLABELED -> R.string.report_reason_sexual_unlabeled
        ReportReasons.REASON_SEXUAL_OTHER -> R.string.report_reason_sexual_other
        ReportReasons.REASON_CHILD_SAFETY_CSAM -> R.string.report_reason_child_safety_csam
        ReportReasons.REASON_CHILD_SAFETY_GROOM -> R.string.report_reason_child_safety_groom
        ReportReasons.REASON_CHILD_SAFETY_PRIVACY -> R.string.report_reason_child_safety_privacy
        ReportReasons.REASON_CHILD_SAFETY_HARASSMENT -> R.string.report_reason_child_safety_harassment
        ReportReasons.REASON_CHILD_SAFETY_OTHER -> R.string.report_reason_child_safety_other
        ReportReasons.REASON_HARASSMENT_TROLL -> R.string.report_reason_harassment_troll
        ReportReasons.REASON_HARASSMENT_TARGETED -> R.string.report_reason_harassment_targeted
        ReportReasons.REASON_HARASSMENT_HATE_SPEECH -> R.string.report_reason_harassment_hate_speech
        ReportReasons.REASON_HARASSMENT_DOXXING -> R.string.report_reason_harassment_doxxing
        ReportReasons.REASON_HARASSMENT_OTHER -> R.string.report_reason_harassment_other
        ReportReasons.REASON_MISLEADING_BOT -> R.string.report_reason_misleading_bot
        ReportReasons.REASON_MISLEADING_IMPERSONATION -> R.string.report_reason_misleading_impersonation
        ReportReasons.REASON_MISLEADING_SPAM -> R.string.report_reason_misleading_spam
        ReportReasons.REASON_MISLEADING_SCAM -> R.string.report_reason_misleading_scam
        ReportReasons.REASON_MISLEADING_ELECTIONS -> R.string.report_reason_misleading_elections
        ReportReasons.REASON_MISLEADING_OTHER -> R.string.report_reason_misleading_other
        ReportReasons.REASON_RULE_SITE_SECURITY -> R.string.report_reason_rule_site_security
        ReportReasons.REASON_RULE_PROHIBITED_SALES -> R.string.report_reason_rule_prohibited_sales
        ReportReasons.REASON_RULE_BAN_EVASION -> R.string.report_reason_rule_ban_evasion
        ReportReasons.REASON_RULE_OTHER -> R.string.report_reason_rule_other
        ReportReasons.REASON_SELF_HARM_CONTENT -> R.string.report_reason_self_harm_content
        ReportReasons.REASON_SELF_HARM_ED -> R.string.report_reason_self_harm_ed
        ReportReasons.REASON_SELF_HARM_STUNTS -> R.string.report_reason_self_harm_stunts
        ReportReasons.REASON_SELF_HARM_SUBSTANCES -> R.string.report_reason_self_harm_substances
        ReportReasons.REASON_SELF_HARM_OTHER -> R.string.report_reason_self_harm_other
        ReportReasons.REASON_OTHER -> R.string.report_reason_other
        else -> R.string.report_reason_unknown_fallback
    }

/**
 * Display order for the Category step. Matches social-app's
 * ReportDialog card order (Spam first as the most-common reason; Other
 * last as the fallback). Sub-reason ordering inside each category lives
 * on the [ReportCategory] sealed sum (PR 1's foundation).
 *
 * Typed as `ImmutableList` (not `List`) so Compose's stability
 * inference can treat the `CategoryStep` parameter chain as stable —
 * the Kotlin `List` interface is unstable even when the underlying
 * backing collection never mutates.
 */
private val CATEGORY_ORDER: ImmutableList<ReportCategory> =
    persistentListOf(
        ReportCategory.Spam,
        ReportCategory.Misleading,
        ReportCategory.Harassment,
        ReportCategory.Sexual,
        ReportCategory.Violence,
        ReportCategory.ChildSafety,
        ReportCategory.SelfHarm,
        ReportCategory.RuleViolation,
        ReportCategory.Other,
    )
