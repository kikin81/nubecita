package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.foundation.layout.size
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
 * the grouping to the shared [NubecitaListGroup] design-system component — one
 * rounded [surfaceContainer] card, flush rows, hairline dividers, outer-only
 * corners (the Google Play settings-sheet look) — so this file only maps a
 * [SettingsRow] to row content and no longer hand-computes first/last shapes.
 *
 * Empty sections render nothing (caption included) per the group component.
 */
@Composable
internal fun SettingsSection(
    rows: ImmutableList<SettingsRow>,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    NubecitaListGroup(items = rows, modifier = modifier, label = label) { row ->
        SettingsRowContent(row)
    }
}

@Composable
private fun SettingsRowContent(row: SettingsRow) {
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
                headlineContent = headline,
                onClick = row.onClick,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
            )
        is SettingsRow.Link ->
            NubecitaListItem(
                headlineContent = headline,
                onClick = row.onClick,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
            )
        is SettingsRow.Toggle ->
            NubecitaListItem(
                headlineContent = headline,
                // Whole row toggles the switch (matches the trailing Switch).
                onClick = { row.onCheckedChange(!row.checked) },
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                trailingContent = {
                    Switch(checked = row.checked, onCheckedChange = row.onCheckedChange)
                },
            )
        is SettingsRow.Picker ->
            NubecitaListItem(
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
                headlineContent = headline,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
            )
    }
}
