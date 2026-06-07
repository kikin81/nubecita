package net.kikin.nubecita.feature.profile.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.image.CropImage
import net.kikin.nubecita.core.image.CropShape
import net.kikin.nubecita.core.image.rememberImagePicker
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Stateful own-profile edit screen (text-first slice). Owns the
 * [EditProfileViewModel] + effect collector + snackbar host, and routes the
 * app-bar up / system back through [EditProfileEvent.BackPressed] so the
 * unsaved-changes guard runs in the VM. [onNavigateBack] pops the sub-route.
 *
 * A `@MainShell` sub-route, so the bottom nav is hidden on phones and the
 * screen owns its own insets via `Scaffold` + `imePadding()` on the scroll
 * column (MainActivity is `adjustResize`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileScreen(
    viewModel: EditProfileViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // The effect collector restarts on Unit, not callback identity — capture
    // the latest onNavigateBack to avoid pinning a stale lambda.
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    val swapConflictMsg = stringResource(R.string.edit_profile_error_swap_conflict)
    val unauthorizedMsg = stringResource(R.string.edit_profile_error_unauthorized)
    val networkMsg = stringResource(R.string.edit_profile_error_network)

    LaunchedEffect(Unit) {
        // Capture the scope so each snackbar runs in its own child job — an
        // inline suspending showSnackbar would let a dismiss-interrupt cancel
        // the whole collector (same guard as SettingsScreen).
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                EditProfileEffect.NavigateBack -> currentOnNavigateBack()
                is EditProfileEffect.ShowError -> {
                    val message =
                        when (effect.error) {
                            SaveError.SwapConflict -> swapConflictMsg
                            SaveError.Unauthorized -> unauthorizedMsg
                            SaveError.Network -> networkMsg
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(message)
                    }
                }
            }
        }
    }

    // System back: cancel an active crop first, then dismiss the discard dialog,
    // else run the dirty guard. (The crop surface has no BackHandler of its own.)
    BackHandler {
        when {
            state.croppingTarget != null -> viewModel.handleEvent(EditProfileEvent.CropCancelled)
            state.showDiscardDialog -> viewModel.handleEvent(EditProfileEvent.DiscardDismissed)
            else -> viewModel.handleEvent(EditProfileEvent.BackPressed)
        }
    }

    // Single-image pickers, one per slot, each routing to the matching slot.
    val pickAvatar =
        rememberImagePicker(remainingCapacity = 1) { picked ->
            picked.firstOrNull()?.let { viewModel.handleEvent(EditProfileEvent.ImagePicked(ImageSlotKind.Avatar, it.uri)) }
        }
    val pickBanner =
        rememberImagePicker(remainingCapacity = 1) { picked ->
            picked.firstOrNull()?.let { viewModel.handleEvent(EditProfileEvent.ImagePicked(ImageSlotKind.Banner, it.uri)) }
        }

    val cropping = state.croppingTarget
    if (cropping != null) {
        // Full-screen crop surface, modal over the form (form state is preserved
        // in the VM); confirm/cancel route back to the slot.
        CropImage(
            sourceUri = cropping.uri,
            shape = if (cropping.slot == ImageSlotKind.Avatar) CropShape.AvatarCircle else CropShape.Banner,
            onCrop = { bytes, mime -> viewModel.handleEvent(EditProfileEvent.ImageCropped(cropping.slot, bytes, mime)) },
            onCancel = { viewModel.handleEvent(EditProfileEvent.CropCancelled) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    EditProfileScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onPickAvatar = pickAvatar,
        onPickBanner = pickBanner,
        onBack = { viewModel.handleEvent(EditProfileEvent.BackPressed) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless EditProfile screen chrome — the `Scaffold` + app bar (title, back,
 * Save / saving spinner) + the form + discard dialog, driven purely by [state]
 * and [onEvent]. Split out so previews and screenshot tests can exercise the
 * full screen (app bar + Save-button states) without a ViewModel; the stateful
 * [EditProfileScreen] owns the VM, effects, pickers, and crop overlay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileScreenContent(
    state: EditProfileViewState,
    onEvent: (EditProfileEvent) -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.edit_profile_back_content_description),
                        )
                    }
                },
                actions = {
                    if (state.isSaving) {
                        NubecitaWavyProgressIndicator(
                            modifier = Modifier.padding(end = 4.dp).size(20.dp),
                        )
                    } else {
                        TextButton(
                            onClick = { onEvent(EditProfileEvent.SaveTapped) },
                            enabled = state.canSave,
                        ) {
                            Text(stringResource(R.string.edit_profile_save))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        EditProfileContent(
            state = state,
            onEvent = onEvent,
            onPickAvatar = onPickAvatar,
            onPickBanner = onPickBanner,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }

    if (state.showDiscardDialog) {
        DiscardChangesDialog(
            onConfirm = { onEvent(EditProfileEvent.DiscardConfirmed) },
            onDismiss = { onEvent(EditProfileEvent.DiscardDismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileContent(
    state: EditProfileViewState,
    onEvent: (EditProfileEvent) -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        EditProfileImages(
            banner = state.banner,
            avatar = state.avatar,
            onPickBanner = onPickBanner,
            onPickAvatar = onPickAvatar,
            onRemoveBanner = { onEvent(EditProfileEvent.RemoveImage(ImageSlotKind.Banner)) },
            onRemoveAvatar = { onEvent(EditProfileEvent.RemoveImage(ImageSlotKind.Avatar)) },
        )

        OutlinedTextField(
            value = state.displayName,
            onValueChange = { onEvent(EditProfileEvent.DisplayNameChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            // Lock the fields while the save is in flight so edits made after
            // tapping Save can't be silently dropped by the navigate-back.
            enabled = !state.isSaving,
            label = { Text(stringResource(R.string.edit_profile_display_name_label)) },
            singleLine = true,
            isError = state.isDisplayNameOverLimit,
            supportingText = {
                Text(
                    text =
                        stringResource(
                            R.string.edit_profile_grapheme_counter,
                            state.displayNameGraphemes,
                            MAX_DISPLAY_NAME_GRAPHEMES,
                        ),
                )
            },
        )

        OutlinedTextField(
            value = state.description,
            onValueChange = { onEvent(EditProfileEvent.DescriptionChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
            label = { Text(stringResource(R.string.edit_profile_bio_label)) },
            minLines = 3,
            maxLines = 8,
            isError = state.isDescriptionOverLimit,
            supportingText = {
                Text(
                    text =
                        stringResource(
                            R.string.edit_profile_grapheme_counter,
                            state.descriptionGraphemes,
                            MAX_DESCRIPTION_GRAPHEMES,
                        ),
                )
            },
        )
    }
}

@Composable
private fun DiscardChangesDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_profile_discard_title)) },
        text = { Text(stringResource(R.string.edit_profile_discard_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.edit_profile_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.edit_profile_discard_cancel))
            }
        },
    )
}

// Co-located previews for the in-editor dev loop. Screenshot baselines for
// these states live in EditProfileScreenshotTest (the @PreviewTest harness).

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Edit profile — with images", showBackground = true)
@Composable
private fun EditProfileScreenWithImagesPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileScreenContent(
            state =
                EditProfileViewState(
                    displayName = "Alice",
                    description = "Coffee, Kotlin, and cloud-watching.",
                    displayNameGraphemes = 5,
                    descriptionGraphemes = 35,
                    avatar = ImageSlot.Original("https://example.test/avatar.jpg"),
                    banner = ImageSlot.Original("https://example.test/banner.jpg"),
                    isDirty = true,
                ),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Edit profile — empty", showBackground = true)
@Composable
private fun EditProfileScreenEmptyPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileScreenContent(
            state = EditProfileViewState(),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
            onBack = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Edit profile — saving", showBackground = true)
@Composable
private fun EditProfileScreenSavingPreview() {
    NubecitaCanvasPreviewTheme {
        EditProfileScreenContent(
            state =
                EditProfileViewState(
                    displayName = "Alice",
                    description = "Coffee, Kotlin, and cloud-watching.",
                    displayNameGraphemes = 5,
                    descriptionGraphemes = 35,
                    isDirty = true,
                    isSaving = true,
                ),
            onEvent = {},
            onPickAvatar = {},
            onPickBanner = {},
            onBack = {},
        )
    }
}
