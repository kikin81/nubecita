package net.kikin.nubecita.feature.composer.impl.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.composer.impl.R

/**
 * Stateless audience-picker content, Material 3 Expressive treatment: the reply
 * options and the interaction settings each render as a grouped tonal card of
 * [SegmentedListItem]s (position-aware expressive corners on a `surfaceContainer`
 * tone, leading icons), the same grouped-list pattern Settings / Chats use.
 *
 * "Who can reply" is the mutually-exclusive **Anyone** / **Nobody** presets
 * (radios) plus the **followers / following / mentioned** combination
 * (checkboxes); the second card carries the **allow quote posts** switch and the
 * **save as default** checkbox. A "Who can interact?" header with Reset and a
 * Cancel / Done footer bracket the cards.
 *
 * Pure projection of [audience] + [saveAsDefault] — all selection transitions go
 * through the pure helpers in `AudiencePickerLogic` ([isAnyone], [isChecked],
 * [toggle], …). The hosting [AudiencePicker] owns the draft; [onConfirm] commits
 * it, [onDismiss] discards. "Select from your lists" is shown disabled until
 * list support lands (nubecita-33bw.6).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.composer_audience_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset, modifier = Modifier.testTag(AUDIENCE_RESET_TEST_TAG)) {
                Text(text = stringResource(R.string.composer_audience_reset))
            }
        }

        SectionLabel(text = stringResource(R.string.composer_audience_reply_section))
        // "Who can reply" — one grouped card; six rows so the index/count drive
        // the position-aware corner shaping (first top-rounded, last bottom).
        val reply = audience.reply
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            AudienceSegmentRow(
                index = 0,
                count = 6,
                icon = NubecitaIconName.Public,
                label = stringResource(R.string.composer_audience_anyone),
                control = RowControl.RADIO,
                selected = reply.isAnyone,
                onToggle = { onAudienceChange(audience.copy(reply = ReplyAudience.Everyone)) },
            )
            AudienceSegmentRow(
                index = 1,
                count = 6,
                icon = NubecitaIconName.LockPerson,
                label = stringResource(R.string.composer_audience_nobody),
                control = RowControl.RADIO,
                selected = reply.isNobody,
                onToggle = { onAudienceChange(audience.copy(reply = ReplyAudience.Nobody)) },
            )
            AudienceSegmentRow(
                index = 2,
                count = 6,
                icon = NubecitaIconName.Person,
                label = stringResource(R.string.composer_audience_followers),
                control = RowControl.CHECKBOX,
                selected = reply.isChecked(ReplyGroup.FOLLOWERS),
                onToggle = { onAudienceChange(audience.copy(reply = reply.toggle(ReplyGroup.FOLLOWERS))) },
            )
            AudienceSegmentRow(
                index = 3,
                count = 6,
                icon = NubecitaIconName.PersonAdd,
                label = stringResource(R.string.composer_audience_following),
                control = RowControl.CHECKBOX,
                selected = reply.isChecked(ReplyGroup.FOLLOWING),
                onToggle = { onAudienceChange(audience.copy(reply = reply.toggle(ReplyGroup.FOLLOWING))) },
            )
            AudienceSegmentRow(
                index = 4,
                count = 6,
                icon = NubecitaIconName.AlternateEmail,
                label = stringResource(R.string.composer_audience_mentioned),
                control = RowControl.CHECKBOX,
                selected = reply.isChecked(ReplyGroup.MENTIONED),
                onToggle = { onAudienceChange(audience.copy(reply = reply.toggle(ReplyGroup.MENTIONED))) },
            )
            // Deferred (nubecita-33bw.6): list-scoped replies aren't supported yet.
            AudienceSegmentRow(
                index = 5,
                count = 6,
                icon = NubecitaIconName.Article,
                label = stringResource(R.string.composer_audience_lists),
                control = RowControl.CHECKBOX,
                selected = false,
                enabled = false,
                onToggle = {},
            )
        }

        Spacer(Modifier.height(16.dp))
        // Interaction settings — quotes + save-as-default in their own card.
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            AudienceSegmentRow(
                index = 0,
                count = 2,
                icon = NubecitaIconName.FormatQuote,
                label = stringResource(R.string.composer_audience_allow_quotes),
                control = RowControl.SWITCH,
                selected = audience.allowQuotes,
                onToggle = { onAudienceChange(audience.copy(allowQuotes = !audience.allowQuotes)) },
            )
            AudienceSegmentRow(
                index = 1,
                count = 2,
                icon = NubecitaIconName.Bookmark,
                label = stringResource(R.string.composer_audience_save_default),
                control = RowControl.CHECKBOX,
                selected = saveAsDefault,
                onToggle = { onSaveAsDefaultChange(!saveAsDefault) },
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.composer_audience_picker_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onConfirm, modifier = Modifier.testTag(AUDIENCE_CONFIRM_TEST_TAG)) {
                Text(text = stringResource(R.string.composer_audience_picker_done))
            }
        }
    }
}

internal const val AUDIENCE_RESET_TEST_TAG: String = "audience_picker_reset"
internal const val AUDIENCE_CONFIRM_TEST_TAG: String = "audience_picker_confirm"

private enum class RowControl { RADIO, CHECKBOX, SWITCH }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}

/**
 * One grouped-list row: a [SegmentedListItem] on the `surfaceContainer` tone with
 * position-aware corners ([ListItemDefaults.segmentedShapes]), a leading [icon], a
 * [label], and a trailing [control] (radio / checkbox / switch).
 *
 * The row dispatches to the **selectable** ([RowControl.RADIO]) or **toggleable**
 * ([RowControl.CHECKBOX] / [RowControl.SWITCH]) `SegmentedListItem` overload, which
 * bakes `Role.RadioButton` / `Role.Checkbox` + the selected / toggle state onto the
 * single merged row node. The trailing control is therefore purely visual
 * (`onClick` / `onCheckedChange` = `null`, so it contributes no competing
 * semantics): the whole row is one focusable target and TalkBack announces it once
 * ("People you follow, checkbox, not checked") instead of focusing the row and the
 * control separately. [onToggle] drives the row's selection. [enabled]` = false`
 * dims the icon + label and disables the row for the deferred "lists" entry.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AudienceSegmentRow(
    index: Int,
    count: Int,
    icon: NubecitaIconName,
    label: String,
    control: RowControl,
    selected: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
) {
    val iconTint =
        if (enabled) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        }
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    val leadingContent: @Composable () -> Unit = {
        NubecitaIcon(name = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = iconTint)
    }
    val labelContent: @Composable () -> Unit = {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) Color.Unspecified else LocalContentColor.current.copy(alpha = 0.38f),
        )
    }
    when (control) {
        // Single-selection overload: applySemantics sets selected + Role.RadioButton.
        RowControl.RADIO ->
            SegmentedListItem(
                selected = selected,
                onClick = onToggle,
                enabled = enabled,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = { RadioButton(selected = selected, onClick = null, enabled = enabled) },
                content = labelContent,
            )
        // Multi-selection (toggleable) overload: applySemantics sets the toggle
        // state + Role.Checkbox. M3 sanctions a Switch as trailing content here too.
        RowControl.CHECKBOX ->
            SegmentedListItem(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
                content = labelContent,
            )
        RowControl.SWITCH ->
            SegmentedListItem(
                checked = selected,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                trailingContent = { Switch(checked = selected, onCheckedChange = null, enabled = enabled) },
                content = labelContent,
            )
    }
}
