package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.feature.composer.impl.internal.ComposerAttachmentChip
import net.kikin.nubecita.feature.composer.impl.internal.ComposerCharacterCounter
import net.kikin.nubecita.feature.composer.impl.internal.ComposerPostButton
import net.kikin.nubecita.feature.composer.impl.internal.rememberComposerImagePicker
import net.kikin.nubecita.feature.composer.impl.state.ComposerEffect
import net.kikin.nubecita.feature.composer.impl.state.ComposerEvent
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
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
    onSubmitSuccess: (AtUri) -> Unit,
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
    // Without `remember`, every keystroke mutates `state`, recomposes
    // ComposerScreen, and reallocates these lambdas — which invalidates
    // ComposerScreenContent's skip and cascades into ComposerPostButton
    // on the hot path. `viewModel` is the only closed-over dependency
    // and is stable for the screen's lifetime.
    val onTextChange =
        remember(viewModel) {
            { text: String -> viewModel.handleEvent(ComposerEvent.TextChanged(text)) }
        }
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
                is ComposerEffect.OnSubmitSuccess -> currentOnSubmitSuccess(effect.newPostUri)
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
        snackbarHostState = snackbarHostState,
        onTextChange = onTextChange,
        onSubmit = onSubmit,
        onCloseClick = onNavigateBack,
        onAddImageClick = onAddImageClick,
        onRemoveAttachment = onRemoveAttachment,
        modifier = modifier,
    )
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
    snackbarHostState: SnackbarHostState,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCloseClick: () -> Unit,
    onAddImageClick: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
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
                        enabled = canPost(state),
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
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
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
 */
private fun canPost(state: ComposerState): Boolean {
    if (state.text.isBlank() && state.attachments.isEmpty()) return false
    if (state.isOverLimit) return false
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
