package net.kikin.nubecita.feature.composer.impl

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.image.PickedImage
import net.kikin.nubecita.core.image.rememberImagePicker
import net.kikin.nubecita.core.posting.BLUESKY_LANGUAGE_TAGS
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.internal.AltEditorLayer
import net.kikin.nubecita.feature.composer.impl.internal.AudiencePicker
import net.kikin.nubecita.feature.composer.impl.internal.ComposerAttachmentChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerAudienceChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerCharacterCounter
import net.kikin.nubecita.feature.composer.impl.internal.ComposerCloseAction
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDialogAction
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDiscardDialog
import net.kikin.nubecita.feature.composer.impl.internal.ComposerLanguageChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerOptionsChipRow
import net.kikin.nubecita.feature.composer.impl.internal.ComposerPostButton
import net.kikin.nubecita.feature.composer.impl.internal.ComposerQuoteSection
import net.kikin.nubecita.feature.composer.impl.internal.ComposerReplyParentSection
import net.kikin.nubecita.feature.composer.impl.internal.ComposerSuggestionList
import net.kikin.nubecita.feature.composer.impl.internal.LanguagePicker
import net.kikin.nubecita.feature.composer.impl.internal.composerCloseAttempt
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.ParentLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.QuoteLoadStatus
import net.kikin.nubecita.feature.composer.impl.state.isGalleryMissingAlt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
 *
 * Suppresses [compose:vm-forwarding-check] (ktlint) and
 * [ComposeViewModelForwarding] (slack compose-lints 1.5.0+, the
 * Android Lint counterpart). The Stateful Screen / Stateless Content
 * split is the project's standard MVI pattern (CLAUDE.md §MVI
 * conventions): `ComposerScreen` owns the VM, observes state, and
 * delegates rendering to `ComposerScreenContent` which takes typed
 * state + callbacks. compose-lints 1.5.0 broadened the rule to trace
 * through any callback expression that captures the VM — including
 * remembered lambdas like the ones below — making the pattern
 * unsuppressible without ripping out the Root/Content split across
 * every screen. Same Root/Content forwarding-suppress as the other MVI
 * screens. Revisit if a project-wide refactor away from Root/Content
 * is ever scoped.
 */
@Suppress("ktlint:compose:vm-forwarding-check", "ComposeViewModelForwarding")
@Composable
internal fun ComposerScreen(
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
    val replyNotAllowedErrorMessage = stringResource(R.string.composer_error_reply_not_allowed)
    val quoteNotAllowedErrorMessage = stringResource(R.string.composer_error_quote_not_allowed)
    val genericErrorMessage = stringResource(R.string.composer_error_generic)
    val uploadFailedTemplate = stringResource(R.string.composer_error_upload_failed)
    val audienceSaveErrorMessage = stringResource(R.string.composer_audience_save_error)

    // Stabilize the unstable lambda params via rememberUpdatedState so
    // the LaunchedEffect's restart key (Unit) doesn't capture a stale
    // reference if the composer screen is composed with new callbacks.
    // The Compose-ktlint rule `lambda-param-in-effect` enforces this.
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)
    val currentOnSubmitSuccess by rememberUpdatedState(onSubmitSuccess)

    // The host Activity (not the composer route / LocalLifecycleOwner) owns the
    // scope for the post-publish in-app review request, so it survives this
    // screen popping on success (design D3). Non-null in the app, like the
    // paywall purchase flow; null in previews/tests, where the request is skipped.
    val reviewActivity = LocalActivity.current as? ComponentActivity

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
            { picked: List<PickedImage> ->
                // Map the neutral `:core:image` picker result onto the
                // composer's own attachment model at this boundary —
                // `:core:image` carries no posting knowledge.
                viewModel.handleEvent(
                    ComposerEvent.AddAttachments(
                        picked.map { ComposerAttachment(uri = it.uri, mimeType = it.mimeType) },
                    ),
                )
            }
        }
    val onRemoveAttachment =
        remember(viewModel) {
            { index: Int -> viewModel.handleEvent(ComposerEvent.RemoveAttachment(index)) }
        }
    val onMoveAttachment =
        remember(viewModel) {
            { from: Int, to: Int -> viewModel.handleEvent(ComposerEvent.MoveAttachment(from, to)) }
        }
    val onOpenAltEditor =
        remember(viewModel) {
            { index: Int -> viewModel.handleEvent(ComposerEvent.OpenAltEditor(index)) }
        }
    val onCloseAltEditor =
        remember(viewModel) {
            { viewModel.handleEvent(ComposerEvent.CloseAltEditor) }
        }
    val onSetAltText =
        remember(viewModel) {
            { index: Int, text: String -> viewModel.handleEvent(ComposerEvent.SetAltText(index, text)) }
        }
    val onSuggestionClick =
        remember(viewModel) {
            { actor: ActorUi ->
                viewModel.handleEvent(ComposerEvent.TypeaheadResultClicked(actor))
            }
        }
    val onRetryParentLoad =
        remember(viewModel) {
            { viewModel.handleEvent(ComposerEvent.RetryParentLoad) }
        }
    val onRetryQuoteLoad =
        remember(viewModel) {
            { viewModel.handleEvent(ComposerEvent.RetryQuoteLoad) }
        }
    val onRemoveQuote =
        remember(viewModel) {
            { viewModel.handleEvent(ComposerEvent.RemoveQuote) }
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
    // Audience-picker visibility — same shape as showPicker; the draft lives
    // inside AudiencePicker via its own rememberSaveable.
    var showAudiencePicker by rememberSaveable { mutableStateOf(false) }
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
    // While an overlay (a picker or the discard dialog) is showing, the
    // back-press dismisses THAT overlay rather than running `attemptClose()`.
    // This BackHandler fires before the overlays' own back handling (the
    // ModalBottomSheet / Popup `dismissOnBackPress`), so without handling them
    // explicitly here a back-press while the audience or language picker is open
    // would fall through to `attemptClose()` and could close the whole composer
    // mid-selection. Treating in-overlay back-press as Cancel matches the M3
    // dismissal convention.
    BackHandler(enabled = true) {
        when {
            showAudiencePicker -> showAudiencePicker = false
            showPicker -> showPicker = false
            showDiscardDialog -> showDiscardDialog = false
            else -> attemptClose()
        }
    }

    // Picker plumbing. The contract is captured at registration time
    // by `rememberLauncherForActivityResult`, so we re-key the helper
    // on `remainingCapacity` to keep the picker UI honest as the user
    // adds / removes attachments. See `:core:image`'s `ImagePicker.kt`.
    val remainingCapacity = ComposerViewModel.MAX_ATTACHMENTS - state.attachments.size
    val onAddImageClick =
        rememberImagePicker(
            remainingCapacity = remainingCapacity,
            onPick = onAddAttachments,
        )

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ComposerEffect.NavigateBack -> currentOnNavigateBack()
                is ComposerEffect.OnSubmitSuccess -> {
                    // Fire-and-forget the in-app review request on the Activity's
                    // scope so it outlives the composer pop below. All gating +
                    // fail-silence live in ReviewManager; the bench flavor no-ops.
                    reviewActivity?.let { act ->
                        act.lifecycleScope.launch { viewModel.reviewManager.onPostPublished(act) }
                    }
                    currentOnSubmitSuccess(effect.newPostUri, effect.replyToUri)
                }
                is ComposerEffect.ShowError -> {
                    val message =
                        when (val cause = effect.error) {
                            is ComposerError.Network -> networkErrorMessage
                            ComposerError.Unauthorized -> unauthorizedErrorMessage
                            ComposerError.ParentNotFound -> parentNotFoundErrorMessage
                            ComposerError.ReplyNotAllowed -> replyNotAllowedErrorMessage
                            ComposerError.QuoteNotAllowed -> quoteNotAllowedErrorMessage
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
                ComposerEffect.ShowAudienceSaveError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(message = audienceSaveErrorMessage)
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
        onMoveAttachment = onMoveAttachment,
        onOpenAltEditor = onOpenAltEditor,
        onCloseAltEditor = onCloseAltEditor,
        onSetAltText = onSetAltText,
        onSuggestionClick = onSuggestionClick,
        onRetryParentLoad = onRetryParentLoad,
        onRetryQuoteLoad = onRetryQuoteLoad,
        onRemoveQuote = onRemoveQuote,
        onLanguageChipClick = { showPicker = true },
        onAudienceChipClick = { showAudiencePicker = true },
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

    if (showAudiencePicker) {
        AudiencePicker(
            initialAudience = state.audience,
            onConfirm = { audience, saveAsDefault ->
                viewModel.handleEvent(ComposerEvent.AudienceSelectionConfirmed(audience, saveAsDefault))
                showAudiencePicker = false
            },
            onDismiss = { showAudiencePicker = false },
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
internal fun ComposerScreenContent(
    state: ComposerState,
    textFieldState: TextFieldState,
    snackbarHostState: SnackbarHostState,
    deviceLocaleTag: String,
    onSubmit: () -> Unit,
    onCloseClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSuggestionClick: (ActorUi) -> Unit,
    onRetryParentLoad: () -> Unit,
    onRetryQuoteLoad: () -> Unit,
    onRemoveQuote: () -> Unit,
    onLanguageChipClick: () -> Unit,
    onAudienceChipClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Optional (defaulted) so static previews / screenshot fixtures need not
    // thread them — they never reorder or edit alt. The live screen passes the
    // real VM callbacks. Placed after `modifier` per the Compose param-order rule.
    onMoveAttachment: (Int, Int) -> Unit = { _, _ -> },
    onOpenAltEditor: (Int) -> Unit = {},
    onCloseAltEditor: () -> Unit = {},
    onSetAltText: (Int, String) -> Unit = { _, _ -> },
) {
    // Alt editor is a layer within the composer's own surface: when a photo is
    // being described, it replaces the composer body (full-screen on phone, or
    // within the composer's dialog card on tablet — inherited, no second route).
    // The composer body's text lives on the VM's TextFieldState, so swapping the
    // subtree out and back loses nothing.
    val altTarget = state.altEditTarget
    if (altTarget != null) {
        AltEditorLayer(
            attachments = state.attachments,
            initialIndex = altTarget,
            onSetAlt = onSetAltText,
            onClose = onCloseAltEditor,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

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
        containerColor = MaterialTheme.colorScheme.surface,
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
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            filled = true,
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
        // Canvas paint is owned by the Scaffold's containerColor = surface.
        // Apply the Scaffold's inset padding here, then the content's
        // own 16dp/8dp gutter on top.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        .focusRequester(focusRequester)
                        // Stable tag (surfaced as a bare resource-id via
                        // testTagsAsResourceId) so the baseline-profile journey
                        // can focus + type into the composer (nubecita-ioe5).
                        .testTag("composer_text_field"),
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
                // Audience chip — top-level posts only. threadgate/postgate
                // belong to the thread root, so it's hidden in reply mode.
                if (state.replyToUri == null) {
                    ComposerAudienceChip(
                        audience = state.audience,
                        onClick = onAudienceChipClick,
                    )
                }
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
                onMoveAttachment = onMoveAttachment,
                onChipClick = onOpenAltEditor,
            )
            // Gallery required-alt hint: explains why Post is disabled when a
            // >4-image gallery still has an undescribed photo. The chip ALT
            // badges + the editor filmstrip show which ones.
            if (state.isGalleryMissingAlt) {
                Text(
                    text = stringResource(R.string.composer_alt_required_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Quote-mode section — renders nothing when not quoting
            // (quotePostLoad == null). Sits at the bottom (below text +
            // attachments), matching the reader's stacked layout: reply
            // context on top, your text, then the quoted post.
            ComposerQuoteSection(
                status = state.quotePostLoad,
                onRetryClick = onRetryQuoteLoad,
                onRemoveClick = onRemoveQuote,
            )
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
    onMoveAttachment: (Int, Int) -> Unit,
    onChipClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val attachmentCount = attachments.size
    val isAtCap = attachmentCount >= ComposerViewModel.MAX_ATTACHMENTS
    val canAddImage = !isSubmitting && !isAtCap
    // The "Add image" button lives OUTSIDE the LazyRow, so LazyRow item
    // indices map 1:1 to attachment indices — onMove can forward them as-is.
    val listState = rememberLazyListState()
    val reorderableState =
        rememberReorderableLazyListState(listState) { from, to ->
            onMoveAttachment(from.index, to.index)
        }
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
                NubecitaIcon(
                    name = NubecitaIconName.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.composer_add_image_action),
                )
            }
        }
        if (attachmentCount > 0) {
            LazyRow(
                state = listState,
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
                    ReorderableItem(reorderableState, key = attachment.uri.toString()) { _ ->
                        ComposerAttachmentChip(
                            attachment = attachment,
                            enabled = !isSubmitting,
                            // Inline lambda capture is fine here — the
                            // LazyRow item subcomposition is bounded and
                            // `onRemoveAttachment` is already stable
                            // (hoisted via `remember(viewModel)` upstream).
                            onRemoveClick = { onRemoveAttachment(index) },
                            // Tap the chip to describe it (alt editor); long-press
                            // starts a drag-to-reorder; the X tap removes. Tap is
                            // gated off while submitting, matching remove/drag.
                            onClick = if (isSubmitting) null else ({ onChipClick(index) }),
                            // Disabled while submitting (the upload pipeline
                            // reads the list — mutations would race awaitAll).
                            modifier = Modifier.longPressDraggableHandle(enabled = !isSubmitting),
                        )
                    }
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
    // A loaded quote counts as content — an empty-text quote post is valid
    // (the embed is the content), mirroring the VM's canSubmit.
    val hasQuote = state.quotePostLoad is QuoteLoadStatus.Loaded
    if (textFieldState.text.isBlank() && state.attachments.isEmpty() && !hasQuote) return false
    if (state.isOverLimit) return false
    // Gallery (>4 images) requires alt on every photo — mirrors the VM gate.
    if (state.isGalleryMissingAlt) return false
    // Reply-mode requires the parent to be Loaded — without the
    // parent's CID we can't construct the reply ref. The VM's
    // `canSubmit` enforces the same gate; mirroring it here keeps
    // the button disabled instead of just silently rejecting the
    // tap.
    if (state.replyToUri != null && state.replyParentLoad !is ParentLoadStatus.Loaded) return false
    // Quote-mode requires the quote Loaded and quotable — same gate as the VM.
    if (state.quotePostUri != null && state.quotePostLoad !is QuoteLoadStatus.Loaded) return false
    val quoteLoad = state.quotePostLoad
    if (quoteLoad is QuoteLoadStatus.Loaded && !quoteLoad.post.canViewerQuote) return false
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
