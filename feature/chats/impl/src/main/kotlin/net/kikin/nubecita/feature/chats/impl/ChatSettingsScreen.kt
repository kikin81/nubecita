package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Stateful Chat settings screen ("Who can message you"). Owns the
 * [ChatSettingsViewModel] + effect collector + snackbar host, and delegates
 * rendering to [ChatSettingsScreenContent].
 *
 * A `@MainShell` sub-route tagged `adaptiveDialog()`, so the
 * `AdaptiveDialogSceneStrategy` renders it full-screen on Compact width and as
 * a centered Dialog on Medium / Expanded. [onNavigateBack] pops the route; the
 * close affordance and system back both route through it (save-on-tap persists
 * immediately, so there is no dirty-state guard).
 */
@Composable
internal fun ChatSettingsScreen(
    viewModel: ChatSettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorMsg = stringResource(R.string.chat_settings_save_error)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChatSettingsEffect.ShowSaveError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(saveErrorMsg)
                }
            }
        }
    }

    ChatSettingsScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onClose = onNavigateBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

/**
 * Stateless Chat settings chrome — `Scaffold` + app bar (title + close) + the
 * loading / loaded / error body — driven purely by [state] and [onEvent]. Split
 * out so previews and screenshot tests can exercise every state without a
 * ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatSettingsScreenContent(
    state: ChatSettingsViewState,
    onEvent: (ChatSettingsEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.chat_settings_close_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val status = state.status) {
                ChatSettingsLoadStatus.Loading -> LoadingBody()
                is ChatSettingsLoadStatus.Loaded ->
                    LoadedBody(
                        selected = status.selected,
                        onSelect = { onEvent(ChatSettingsEvent.OptionSelected(it)) },
                    )
                ChatSettingsLoadStatus.LoadError ->
                    ErrorBody(onRetry = { onEvent(ChatSettingsEvent.RetryLoad) })
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NubecitaWavyProgressIndicator()
    }
}

@Composable
private fun LoadedBody(
    selected: AllowIncoming,
    onSelect: (AllowIncoming) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_settings_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        // selectableGroup() + per-row selectable(role = RadioButton) is the
        // canonical M3 single-select pattern: the framework applies the correct
        // selection semantics (TalkBack announces "selected", radio-button role)
        // — RadioButton(onClick = null) defers the click to the whole row.
        Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            ALLOW_INCOMING_OPTIONS.forEach { option ->
                val isSelected = option.value == selected
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option.value) },
                            ).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = isSelected, onClick = null)
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBody(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chat_settings_load_error),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.chat_settings_retry))
        }
    }
}

private data class AllowIncomingOption(
    val value: AllowIncoming,
    val labelRes: Int,
)

// Fixed display order: most-open → most-restrictive (matches the official client).
private val ALLOW_INCOMING_OPTIONS =
    persistentListOf(
        AllowIncomingOption(AllowIncoming.Everyone, R.string.chat_settings_option_everyone),
        AllowIncomingOption(AllowIncoming.Following, R.string.chat_settings_option_following),
        AllowIncomingOption(AllowIncoming.NoOne, R.string.chat_settings_option_none),
    )

// Co-located previews; screenshot baselines live in ChatSettingsScreenshotTest.

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Chat settings — loaded", showBackground = true)
@Composable
private fun ChatSettingsLoadedPreview() {
    NubecitaCanvasPreviewTheme {
        ChatSettingsScreenContent(
            state = ChatSettingsViewState(ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.Following)),
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Chat settings — error", showBackground = true)
@Composable
private fun ChatSettingsErrorPreview() {
    NubecitaCanvasPreviewTheme {
        ChatSettingsScreenContent(
            state = ChatSettingsViewState(ChatSettingsLoadStatus.LoadError),
            onEvent = {},
            onClose = {},
        )
    }
}
