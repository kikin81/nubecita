package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.component.NubecitaListGroup
import net.kikin.nubecita.designsystem.component.NubecitaListItem
import net.kikin.nubecita.designsystem.icon.NubecitaIcon

/**
 * Material 3 Expressive grouped-list section for the Settings home. Delegates
 * the grouping to the shared [NubecitaListGroup] design-system component — M3
 * Expressive segmented rows (separate rounded segments, 2dp gaps, position-aware
 * outer corners) — so this file only maps a [SettingsRow] to row content and
 * forwards the segment `shapes` the group hands each row.
 *
 * Empty sections render nothing (caption included) per the group component.
 */
@Composable
internal fun SettingsSection(
    rows: ImmutableList<SettingsRow>,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    NubecitaListGroup(items = rows, modifier = modifier, label = label) { row, shapes ->
        SettingsRowContent(row, shapes)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsRowContent(
    row: SettingsRow,
    shapes: ListItemShapes,
) {
    // Destructive rows (Sign out, Delete account) tint icon + label with `error`.
    val isDestructive =
        (row is SettingsRow.Action && row.isDestructive) ||
            (row is SettingsRow.Link && row.isDestructive)
    val iconTint =
        if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (isDestructive) MaterialTheme.colorScheme.error else Color.Unspecified

    val leadingContent: (@Composable () -> Unit)? =
        row.icon?.let { iconName ->
            {
                NubecitaIcon(
                    name = iconName,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint,
                )
            }
        }
    val supportingContent: (@Composable () -> Unit)? =
        row.supportingText?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    val headline: @Composable () -> Unit = {
        Text(text = row.label, style = MaterialTheme.typography.bodyLarge, color = labelColor)
    }

    when (row) {
        is SettingsRow.Action ->
            NubecitaListItem(
                shapes = shapes,
                headlineContent = headline,
                onClick = row.onClick,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
            )
        is SettingsRow.Link ->
            NubecitaListItem(
                shapes = shapes,
                headlineContent = headline,
                onClick = row.onClick,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
            )
        is SettingsRow.Toggle ->
            NubecitaListItem(
                shapes = shapes,
                headlineContent = headline,
                // Row-level toggle: the whole row carries the toggle semantics
                // (checked state announced to a screen reader), so the trailing
                // Switch is display-only (onCheckedChange = null) — one
                // interactive node, not two.
                checked = row.checked,
                onCheckedChange = row.onCheckedChange,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                trailingContent = {
                    Switch(checked = row.checked, onCheckedChange = null)
                },
            )
        is SettingsRow.Picker ->
            NubecitaListItem(
                shapes = shapes,
                headlineContent = headline,
                onClick = row.onClick,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                trailingContent = {
                    Text(
                        text = row.currentValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        is SettingsRow.Info ->
            // Non-interactive (no onClick) — announces as text, not a button.
            NubecitaListItem(
                shapes = shapes,
                headlineContent = headline,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
            )
    }
}
