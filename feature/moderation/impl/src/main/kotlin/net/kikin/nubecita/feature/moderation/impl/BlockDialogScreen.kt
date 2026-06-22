package net.kikin.nubecita.feature.moderation.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Block-account confirmation dialog content (rendered inside a
 * `ModalBottomSheet` by `ModerationNavigationModule`). Single-step: a
 * destructive confirm with a retryable inline error. The stateful entry owns
 * the VM + effect collector; [BlockDialogContent] is the pure projection for
 * previews / screenshot tests.
 */
@Composable
internal fun BlockDialogScreen(
    viewModel: BlockDialogViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BlockDialogEffect.RequestDismiss -> currentOnDismiss()
            }
        }
    }

    BlockDialogContent(state = state, onEvent = viewModel::handleEvent, modifier = modifier)
}

@Composable
internal fun BlockDialogContent(
    state: BlockDialogState,
    onEvent: (BlockDialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.block_dialog_title, state.handle),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.block_dialog_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (state.hasError) {
            Text(
                text = stringResource(R.string.block_dialog_error),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onEvent(BlockDialogEvent.OnCancelClicked) },
                enabled = !state.isSubmitting,
            ) {
                Text(stringResource(R.string.block_dialog_cancel))
            }
            Button(
                onClick = { onEvent(BlockDialogEvent.OnConfirmClicked) },
                enabled = !state.isSubmitting,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                if (state.isSubmitting) {
                    // nubecita-allow-raw-progress: in-button micro-spinner with
                    // a tuned strokeWidth the brand wavy component doesn't expose.
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    Text(stringResource(R.string.block_dialog_confirm))
                }
            }
        }
    }
}
