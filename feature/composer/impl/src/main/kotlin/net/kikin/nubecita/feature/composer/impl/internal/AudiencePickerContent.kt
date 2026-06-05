package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Stateless audience-picker content: a "Who can reply" section (the mutually
 * exclusive **Anyone** / **Nobody** presets as radios, plus the **followers /
 * following / mentioned** combination as checkboxes), an "allow quote posts"
 * switch, a "save as default" checkbox, and a Reset / Cancel / Done footer.
 *
 * Pure projection of [audience] + [saveAsDefault] — all selection transitions
 * go through the pure helpers in `AudiencePickerLogic` ([isAnyone], [isChecked],
 * [toggle], …). The hosting [AudiencePicker] owns the draft; [onConfirm] commits
 * it, [onDismiss] discards. "Select from your lists" is shown disabled until
 * list support lands (nubecita-33bw.6).
 */
@Composable
internal fun AudiencePickerContent(
    audience: PostAudience,
    saveAsDefault: Boolean,
    onAudienceChange: (PostAudience) -> Unit,
    onSaveAsDefaultChange: (Boolean) -> Unit,
    onReset: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.composer_audience_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onReset,
                modifier = Modifier.testTag(AUDIENCE_RESET_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.composer_audience_reset))
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.composer_audience_reply_section),
            style = MaterialTheme.typography.titleSmall,
        )

        PresetRow(
            label = stringResource(R.string.composer_audience_anyone),
            selected = audience.reply.isAnyone,
            onSelect = { onAudienceChange(audience.copy(reply = ReplyAudience.Everyone)) },
        )
        PresetRow(
            label = stringResource(R.string.composer_audience_nobody),
            selected = audience.reply.isNobody,
            onSelect = { onAudienceChange(audience.copy(reply = ReplyAudience.Nobody)) },
        )

        GroupRow(
            label = stringResource(R.string.composer_audience_followers),
            checked = audience.reply.isChecked(ReplyGroup.FOLLOWERS),
            onToggle = { onAudienceChange(audience.copy(reply = audience.reply.toggle(ReplyGroup.FOLLOWERS))) },
        )
        GroupRow(
            label = stringResource(R.string.composer_audience_following),
            checked = audience.reply.isChecked(ReplyGroup.FOLLOWING),
            onToggle = { onAudienceChange(audience.copy(reply = audience.reply.toggle(ReplyGroup.FOLLOWING))) },
        )
        GroupRow(
            label = stringResource(R.string.composer_audience_mentioned),
            checked = audience.reply.isChecked(ReplyGroup.MENTIONED),
            onToggle = { onAudienceChange(audience.copy(reply = audience.reply.toggle(ReplyGroup.MENTIONED))) },
        )
        // Deferred (nubecita-33bw.6): list-scoped replies aren't supported yet.
        GroupRow(
            label = stringResource(R.string.composer_audience_lists),
            checked = false,
            enabled = false,
            onToggle = {},
        )

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()

        SwitchRow(
            label = stringResource(R.string.composer_audience_allow_quotes),
            checked = audience.allowQuotes,
            onToggle = { onAudienceChange(audience.copy(allowQuotes = !audience.allowQuotes)) },
        )
        GroupRow(
            label = stringResource(R.string.composer_audience_save_default),
            checked = saveAsDefault,
            onToggle = { onSaveAsDefaultChange(!saveAsDefault) },
        )

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.composer_audience_picker_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onConfirm,
                modifier = Modifier.testTag(AUDIENCE_CONFIRM_TEST_TAG),
            ) {
                Text(text = stringResource(R.string.composer_audience_picker_done))
            }
        }
    }
}

internal const val AUDIENCE_RESET_TEST_TAG: String = "audience_picker_reset"
internal const val AUDIENCE_CONFIRM_TEST_TAG: String = "audience_picker_confirm"

@Composable
private fun PresetRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text = label)
    }
}

@Composable
private fun GroupRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        // Mirror the Checkbox's M3 disabled treatment on the label so the whole
        // row reads as disabled (the deferred "Select from your lists" row).
        Text(
            text = label,
            color = if (enabled) Color.Unspecified else LocalContentColor.current.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() })
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
