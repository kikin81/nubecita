package net.kikin.nubecita.feature.composer.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import net.kikin.nubecita.core.posting.BLUESKY_LANGUAGE_TAGS
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.feature.composer.impl.internal.ComposerAttachmentChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerCharacterCounter
import net.kikin.nubecita.feature.composer.impl.internal.ComposerCloseAction
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDialogAction
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDiscardDialog
import net.kikin.nubecita.feature.composer.impl.internal.ComposerLanguageChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerOptionsChipRow
import net.kikin.nubecita.feature.composer.impl.internal.ComposerPostButton
import net.kikin.nubecita.feature.composer.impl.internal.ComposerReplyParentSection
import net.kikin.nubecita.feature.composer.impl.internal.ComposerSuggestionList
import net.kikin.nubecita.feature.composer.impl.internal.LanguagePicker
import net.kikin.nubecita.feature.composer.impl.internal.composerCloseAttempt
import net.kikin.nubecita.feature.composer.impl.internal.rememberComposerImagePicker
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus
import java.util.Locale
import kotlin.math.max

/**
 * Hilt-aware composer screen. Owns:
 *
 * 1. State collection from [ComposerViewModel.uiState] via
 *    `collectAsStateWithLifecycle()` — pauses collection while the
 *    screen is off-screen so background recompositions don't fire.
 * 2. The single `effects` collector (one [LaunchedEffect]) that
 *    surfaces snackbars + dismissal callbacks — per the MVI
 *    convention every screen has exactly one collector.
 * 3. The `LaunchedEffect(Unit)` that requests text-field focus on
 *    first composition so the IME opens automatically without a
 *    user tap. This is non-negotiable per the spec — composer-then-
 *    tap-to-focus is friction we will not ship.
 * 4. The `FocusRequester` instance, [remember]-d so it survives
 *    recompositions but doesn't leak across navigations.
 *
 * Delegates rendering to [ComposerScreenContent] which previews and
 * screenshot tests can call directly with fixture inputs (no
 * ViewModel, no Hilt graph).
 */
@Composable
fun ComposerScreen(
    onNavigateBack: () -> Unit,
    onSubmitSuccess: (newPostUri: AtUri, replyToUri: String?) -> Unit,
    viewModel: ComposerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Pre-resolve snackbar copy at composition time so locale + dark-mode
    // changes participate in recomposition (lint:
    // LocalContextGetResourceValueCall). Matches the established pattern
    // in feature/feed/impl/.../FeedScreen.kt and
    // feature/postdetail/impl/.../PostDetailScreen.kt — every error is a
    // typed ComposerError variant resolved through a `when` against a
    // pre-resolved string. The `composer_error_upload_failed` template
    // is kept un-formatted so the collector can interpolate the
    // attachment ordinal at emission time without re-tripping the lint.
    val networkErrorMessage = stringResource(R.string.composer_error_network)
    val unauthorizedErrorMessage = stringResource(R.string.composer_error_unauthorized)
    val parentNotFoundErrorMessage = stringResource(R.string.composer_error_parent_not_found)
    val genericErrorMessage = stringResource(R.string.composer_error_generic)
    val uploadFailedTemplate = stringResource(R.string.composer_error_upload_failed)

    // Stabilize the unstable lambda params via rememberUpdatedState so
    // the LaunchedEffect's restart key (Unit) doesn't capture a stale
    // reference if the composer screen is composed with new callbacks.
    // The Compose-ktlint rule `lambda-param-in-effect` enforces this.
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnSubmitSuccess by rememberUpdatedState(onSubmitSuccess)

    // Stabilize the VM-event lambdas that wire ComposerScreenContent.
    // Text input no longer dispatches an event — the IME writes
    // directly to viewModel.textFieldState, observed by the VM via
    // snapshotFlow. Eliminating the value/onValueChange round-trip
    // is the entire reason for the TextFieldState migration; see
    // ComposerViewModel's Kdoc for the rationale.
    val onSubmit =
        remember(viewModel) {
            { viewModel.handleEvent(ComposerEvent.Submit) }
        }
    val onAddAttachments =
        remember(viewModel) {
            { picked: List<ComposerAttachment> ->
                viewModel.handleEvent(ComposerEvent.AddAttachments(picked))
            }
        }
    val onRemoveAttachment =
        remember(viewModel) {
            { index: Int -> viewModel.handleEvent(ComposerEvent.RemoveAttachment(index)) }
        }
    val onSuggestionClick =
        remember(viewModel) {
            { actor: ActorTypeaheadUi ->
                viewModel.handleEvent(ComposerEvent.TypeaheadResultClicked(actor))
            }
        }
    val onRetryParentLoad =
        remember(viewModel) {
            { viewModel.handleEvent(ComposerEvent.RetryParentLoad) }
        }

    // Discard-confirmation gate. The composer is a transient, in-progress
    // surface — leaving it (back-press or toolbar X) while the draft has
    // content would otherwise drop unsent text. `attemptClose` is the
    // single entry point both surfaces route through:
    //
    //  - Submitting   → swallow the close attempt entirely. Back-press
    //    propagating to NavDisplay would tear down the composer mid-
    //    submit on Compact, which the spec explicitly forbids (the X
    //    button is also disabled while submitting).
    //  - hasContent   → show the discard confirmation dialog. The user
    //    has to acknowledge that work is being thrown away.
    //  - empty        → close immediately. No prompt for an empty draft.
    //
    // Dialog visibility is local Compose state, not VM state — the VM is
    // the source of truth for `attachments` / `textFieldState` /
    // `submitStatus`, but the dialog is pure UI mode (per the
    // unified-composer design.md "transient — not stored in state" note).
    // `rememberSaveable` so process death + recreate restores the dialog
    // mid-confirmation.
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    // Language-picker visibility. rememberSaveable so process death +
    // recreate restores the open picker mid-selection — same shape as
    // showDiscardDialog above. The picker's *draft* selection lives
    // inside LanguagePicker via its own rememberSaveable.
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val hasContent by remember(viewModel.textFieldState, state.attachments) {
        derivedStateOf {
            viewModel.textFieldState.text.isNotBlank() || state.attachments.isNotEmpty()
        }
    }
    val isSubmitting = state.submitStatus is ComposerSubmitStatus.Submitting
    val attemptClose: () -> Unit = {
        when (composerCloseAttempt(hasContent = hasContent, isSubmitting = isSubmitting)) {
            ComposerCloseAction.Swallow -> Unit
            ComposerCloseAction.ShowDiscardDialog -> showDiscardDialog = true
            ComposerCloseAction.NavigateBack -> currentOnNavigateBack()
        }
    }
    // BackHandler stays enabled at all times so mid-submit back-presses
    // are swallowed here rather than propagating to NavDisplay's pop
    // handler (which would close the Compact composer route mid-submit).
    //
    // While the discard dialog is showing, the back-press dismisses
    // the dialog instead of re-running `attemptClose()`. Without this
    // guard the user gets trapped in the confirmation: re-running the
    // gate sees `hasContent = true` and re-sets `showDiscardDialog =
    // true`, a no-op that never gives the dialog's own back-press
    // dismissal (the Popup's `dismissOnBackPress` at Medium/Expanded,
    // BasicAlertDialog's `onDismissRequest` at Compact) a chance to
    // run, since this BackHandler fires first. Treating in-dialog
    // back-press as Cancel matches the M3 dismissal convention.
    BackHandler(enabled = true) {
        if (showDiscardDialog) {
            showDiscardDialog = false
        } else {
            attemptClose()
        }
    }

    // Picker plumbing. The contract is captured at registration time
    // by `rememberLauncherForActivityResult`, so we re-key the helper
    // on `remainingCapacity` to keep the picker UI honest as the user
    // adds / removes attachments. See `ComposerImagePicker.kt`.
    val remainingCapacity = ComposerViewModel.MAX_ATTACHMENTS - state.attachments.size
    val onAddImageClick =
        rememberComposerImagePicker(
            remainingCapacity = remainingCapacity,
            onPick = onAddAttachments,
        )

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ComposerEffect.NavigateBack -> currentOnNavigateBack()
                is ComposerEffect.OnSubmitSuccess ->
                    currentOnSubmitSuccess(effect.newPostUri, effect.replyToUri)
                is ComposerEffect.ShowError -> {
                    val message =
                        when (val cause = effect.error) {
                            is ComposerError.Network -> networkErrorMessage
                            ComposerError.Unauthorized -> unauthorizedErrorMessage
                            ComposerError.ParentNotFound -> parentNotFoundErrorMessage
                            is ComposerError.RecordCreationFailed -> genericErrorMessage
                            // attachmentIndex is 0-based internally; users
                            // expect 1-based ordinals ("Image 1 failed",
                            // not "Image 0 failed").
                            is ComposerError.UploadFailed ->
                                uploadFailedTemplate.format(cause.attachmentIndex + 1)
                        }
                    // Replace, don't stack — successive failures during a
                    // flapping connection would otherwise queue snackbars
                    // indefinitely. Same pattern as PostDetailScreen.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = message)
                }
            }
        }
    }

    ComposerScreenContent(
        state = state,
        textFieldState = viewModel.textFieldState,
        snackbarHostState = snackbarHostState,
        deviceLocaleTag = viewModel.deviceLocaleTag,
        onSubmit = onSubmit,
        onCloseClick = attemptClose,
        onAddImageClick = onAddImageClick,
        onRemoveAttachment = onRemoveAttachment,
        onSuggestionClick = onSuggestionClick,
        onRetryParentLoad = onRetryParentLoad,
        onLanguageChipClick = { showPicker = true },
        modifier = modifier,
    )

    if (showPicker) {
        // Caller resolves the initial selection — `selectedLangs` if
        // the user has previously committed one, otherwise the device
        // locale so the device's language is preselected on first open
        // (and the picker's chip label stays consistent with what the
        // repo's wtq.12 default would send on a null-state submit).
        //
        // Tags are normalized to their language portion via
        // `Locale.forLanguageTag(it).language` so they match the bare
        // language tags in BLUESKY_LANGUAGE_TAGS. Without this, a
        // device locale like "en-US" or a previously-committed tag
        // like "ja-JP" would never compare equal to the picker's
        // "en" / "ja" rows, leaving them visually unchecked on
        // first open even though they're conceptually selected.
        // `distinct()` collapses any pair like ["ja-JP", "ja-Hira"]
        // that maps to the same bare language.
        val initial =
            (state.selectedLangs ?: persistentListOf(viewModel.deviceLocaleTag))
                .map { Locale.forLanguageTag(it).language }
                .filter { it.isNotEmpty() }
                .distinct()
                .toImmutableList()
        LanguagePicker(
            allTags = BLUESKY_LANGUAGE_TAGS,
            initialSelection = initial,
            deviceLocaleTag = viewModel.deviceLocaleTag,
            onConfirm = { tags ->
                viewModel.handleEvent(ComposerEvent.LanguageSelectionConfirmed(tags))
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showDiscardDialog) {
        ComposerDiscardDialog(
            actions =
                persistentListOf(
                    ComposerDialogAction(
                        label = R.string.composer_discard_cancel,
                        onClick = { showDiscardDialog = false },
                    ),
                    ComposerDialogAction(
                        label = R.string.composer_discard_confirm,
                        destructive = true,
                        onClick = {
                            showDiscardDialog = false
                            currentOnNavigateBack()
                        },
                    ),
                ),
            onDismiss = { showDiscardDialog = false },
        )
    }
}

/**
 * Pure rendering function for the composer screen. Takes flat
 * inputs, dispatches via lambdas — no ViewModel, no Hilt, no
 * effects collection. Drives previews + screenshot fixtures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreenContent(
    state: ComposerState,
    textFieldState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    deviceLocaleTag: String,
    onSubmit: () -> Unit,
    onCloseClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSuggestionClick: (ActorTypeaheadUi) -> Unit,
    onRetryParentLoad: () -> Unit,
    onLanguageChipClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // IME auto-focus on first composition. The spec mandates this —
    // no manual tap to start typing. `LaunchedEffect(Unit)` runs
    // once per screen entry; on configuration change Compose's
    // saveable state preserves the focus, so this doesn't fire
    // again unwanted.
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    // Gate close while a submit is in flight. The screen
                    // is hosted in a Nav3 entry with a per-entry
                    // ViewModelStore: tapping close pops the entry, which
                    // clears the VM and cancels the in-flight
                    // viewModelScope coroutine — silently dropping the
                    // post. Disabling matches how the Post button gates
                    // re-tap during Submitting. Discard-while-submitting
                    // confirmation is wtq.8's scope.
                    IconButton(
                        onClick = onCloseClick,
                        enabled = state.submitStatus !is ComposerSubmitStatus.Submitting,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.composer_close_action),
                        )
                    }
                },
                actions = {
                    ComposerCharacterCounter(
                        graphemeCount = state.graphemeCount,
                        isOverLimit = state.isOverLimit,
                        contentDescription = counterContentDescription(state),
                        modifier = Modifier,
                    )
                    // Reserved-spacer slot between the counter and Post
                    // for the future drafts entry icon (see the
                    // forward-compat note in design.md). Not yet
                    // populated; leaving the gap so wtq.4 → :core:drafts
                    // doesn't reflow the action row when drafts ship.
                    ComposerPostButton(
                        enabled = canPost(state, textFieldState),
                        submitStatus = state.submitStatus,
                        onClick = onSubmit,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(),
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start,
            ) {
                // Reply-mode parent context — renders nothing in
                // new-post mode (replyParentLoad == null), a
                // skeleton while Loading, the parent-post card
                // when Loaded, and an inline retry tile when
                // Failed.
                ComposerReplyParentSection(
                    status = state.replyParentLoad,
                    onRetryClick = onRetryParentLoad,
                )
                OutlinedTextField(
                    state = textFieldState,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    placeholder = { Text(text = stringResource(R.string.composer_text_field_placeholder)) },
                    label = { Text(text = stringResource(R.string.composer_text_field_label)) },
                    isError = state.isOverLimit,
                    enabled = state.submitStatus !is ComposerSubmitStatus.Submitting,
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default,
                        ),
                )
                // `@`-mention typeahead surface — renders only on
                // Suggestions / NoResults, hidden in Idle / Querying.
                // Sits between the text field and the attachment row
                // so the IME's inset push naturally keeps it visible
                // above the keyboard without explicit anchoring.
                //
                // Hidden entirely while submitting: the VM gates
                // TypeaheadResultClicked on submitInFlight, so a
                // tap during submit is a no-op. Rendering the rows
                // would be a visible control that does nothing —
                // hide them to match the actual behavior.
                if (state.submitStatus !is ComposerSubmitStatus.Submitting) {
                    ComposerSuggestionList(
                        typeahead = state.typeahead,
                        onSuggestionClick = onSuggestionClick,
                    )
                }
                // Composer-options chip row. Hosts the language chip
                // in V1; visibility / threadgate / drafts chips land
                // in the same row in follow-up PRs without further
                // chrome changes. When `nubecita-86m`'s
                // HorizontalFloatingToolbar lands, this row migrates
                // wholesale into the toolbar's content slot.
                ComposerOptionsChipRow {
                    ComposerLanguageChip(
                        selectedLangs = state.selectedLangs,
                        deviceLocaleTag = deviceLocaleTag,
                        onClick = onLanguageChipClick,
                    )
                }
                // Composer attachment action row. Hosts the leading
                // "Add image" affordance and a horizontally-scrolling
                // chip strip of the picked attachments (each chip with
                // a Coil-loaded thumbnail + remove button). Both
                // wtq.5.1 (picker) and wtq.5.2 (chips + remove) are
                // wired in this PR.
                ComposerAttachmentRow(
                    attachments = state.attachments,
                    isSubmitting = state.submitStatus is ComposerSubmitStatus.Submitting,
                    onAddImageClick = onAddImageClick,
                    onRemoveAttachment = onRemoveAttachment,
                )
            }
        }
    }
}

/**
 * Horizontal action row beneath the composer's text field — composes
 * the leading "Add image" affordance and a horizontally-scrolling
 * `LazyRow` of [ComposerAttachmentChip]s for the picked attachments.
 *
 * The LazyRow uses each attachment's URI string as its stable key so
 * Compose can survive list reorderings without re-laying-out the
 * surviving chips. The remove-button on each chip is gated off
 * during submission for the same reason the leading "Add image"
 * affordance is — once the upload pipeline starts reading the list,
 * mutations would race the parallel `awaitAll()` in the repository.
 *
 * The leading affordance is hidden (not just disabled) once the
 * composer hits the 4-image cap — keeping it visible at "always
 * disabled" past the cap would draw the eye to a control that can't
 * do anything. While submitting, it stays visible-but-disabled so
 * the user sees the same affordance they tapped a moment ago.
 */
@Composable
private fun ComposerAttachmentRow(
    attachments: ImmutableList<ComposerAttachment>,
    isSubmitting: Boolean,
    onAddImageClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachmentCount = attachments.size
    val isAtCap = attachmentCount >= ComposerViewModel.MAX_ATTACHMENTS
    val canAddImage = !isSubmitting && !isAtCap
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isAtCap) {
            IconButton(
                onClick = onAddImageClick,
                enabled = canAddImage,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.composer_add_image_action),
                )
            }
        }
        if (attachmentCount > 0) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = attachments,
                    // URI strings are unique within a single composer
                    // session — the picker doesn't return the same URI
                    // twice and the reducer doesn't dedup beyond that.
                    key = { _, item -> item.uri.toString() },
                ) { index, attachment ->
                    ComposerAttachmentChip(
                        attachment = attachment,
                        enabled = !isSubmitting,
                        // Inline lambda capture is fine here — the
                        // LazyRow item subcomposition is bounded and
                        // `onRemoveAttachment` is already stable
                        // (hoisted via `remember(viewModel)` upstream).
                        onRemoveClick = { onRemoveAttachment(index) },
                    )
                }
            }
        }
    }
}

/**
 * Submit allowed iff: there's content, not over-limit, not in flight,
 * not already succeeded. Reply-mode parent-loaded gate is enforced
 * by the VM's `canSubmit`; this UI gate is a strict subset so the
 * button stays disabled in those cases too.
 *
 * Reads the live text from [textFieldState] rather than mirroring
 * it onto state — see ComposerViewModel's text-ownership exception
 * Kdoc.
 */
private fun canPost(
    state: ComposerState,
    textFieldState: TextFieldState,
): Boolean {
    if (textFieldState.text.isBlank() && state.attachments.isEmpty()) return false
    if (state.isOverLimit) return false
    // Reply-mode requires the parent to be Loaded — without the
    // parent's CID we can't construct the reply ref. The VM's
    // `canSubmit` enforces the same gate; mirroring it here keeps
    // the button disabled instead of just silently rejecting the
    // tap.
    if (state.replyToUri != null && state.replyParentLoad !is ParentLoadStatus.Loaded) return false
    return when (state.submitStatus) {
        ComposerSubmitStatus.Idle -> true
        is ComposerSubmitStatus.Error -> true
        ComposerSubmitStatus.Submitting -> false
        ComposerSubmitStatus.Success -> false
    }
}

@Composable
private fun counterContentDescription(state: ComposerState): String {
    val limit = ComposerViewModel.MAX_GRAPHEMES
    return if (state.isOverLimit) {
        val offset = state.graphemeCount - limit
        stringResource(R.string.composer_chars_over_limit, offset)
    } else {
        val remaining = max(0, limit - state.graphemeCount)
        // `pluralStringResource` selects the right grammatical form per
        // locale's CLDR rules ("1 character remaining" vs "N characters
        // remaining"). The first int is the quantity used for selection;
        // the second is the formatted argument substituted into `%1$d`.
        pluralStringResource(R.plurals.composer_chars_remaining, remaining, remaining)
    }
}
