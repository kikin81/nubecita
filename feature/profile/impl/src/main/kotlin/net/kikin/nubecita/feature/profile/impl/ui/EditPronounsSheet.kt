package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.MAX_PRONOUNS_GRAPHEMES
import net.kikin.nubecita.feature.profile.impl.PronounsSheetState
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Bottom sheet for editing pronouns: preset chips, a free-text field with a
 * grapheme counter, Done, and Remove. Stateless — the draft lives in
 * [PronounsSheetState]; Done is disabled over the limit, Remove when empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPronounsSheet(
    state: PronounsSheetState,
    presets: ImmutableList<String>,
    onPreset: (String) -> Unit,
    onInput: (String) -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        EditPronounsSheetContent(
            state = state,
            presets = presets,
            onPreset = onPreset,
            onInput = onInput,
            onDone = onDone,
            onRemove = onRemove,
        )
    }
}

/** The sheet's body, split out so screenshot tests can render it without a window-level sheet. */
@Composable
internal fun EditPronounsSheetContent(
    state: PronounsSheetState,
    presets: ImmutableList<String>,
    onPreset: (String) -> Unit,
    onInput: (String) -> Unit,
    onDone: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.edit_pronouns_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.edit_pronouns_helper),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            presets.forEach { preset ->
                val selected = state.input.trim() == preset
                FilterChip(
                    selected = selected,
                    onClick = { onPreset(preset) },
                    label = { Text(preset) },
                    leadingIcon =
                        if (selected) {
                            { NubecitaIcon(name = NubecitaIconName.Check, contentDescription = null) }
                        } else {
                            null
                        },
                )
            }
        }
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.edit_profile_pronouns_label)) },
            isError = state.isOverLimit,
            supportingText = {
                Text(
                    text = stringResource(R.string.edit_profile_grapheme_counter, state.graphemes, MAX_PRONOUNS_GRAPHEMES),
                )
            },
        )
        Button(
            onClick = onDone,
            enabled = !state.isOverLimit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.edit_pronouns_done))
        }
        FilledTonalButton(
            onClick = onRemove,
            enabled = state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.edit_pronouns_remove))
        }
    }
}
