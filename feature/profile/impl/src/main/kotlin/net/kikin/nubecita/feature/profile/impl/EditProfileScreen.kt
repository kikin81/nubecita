package net.kikin.nubecita.feature.profile.impl

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

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

    // App-bar up and system back share the dirty guard in the VM.
    BackHandler { viewModel.handleEvent(EditProfileEvent.BackPressed) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.handleEvent(EditProfileEvent.BackPressed) }) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.edit_profile_back_content_description),
                        )
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { viewModel.handleEvent(EditProfileEvent.SaveTapped) },
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
            onEvent = viewModel::handleEvent,
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
        )
    }

    if (state.showDiscardDialog) {
        DiscardChangesDialog(
            onConfirm = { viewModel.handleEvent(EditProfileEvent.DiscardConfirmed) },
            onDismiss = { viewModel.handleEvent(EditProfileEvent.DiscardDismissed) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileContent(
    state: EditProfileViewState,
    onEvent: (EditProfileEvent) -> Unit,
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
