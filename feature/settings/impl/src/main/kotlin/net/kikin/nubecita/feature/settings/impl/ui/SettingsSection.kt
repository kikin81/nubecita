package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.icon.NubecitaIcon

/**
 * Material 3 Expressive grouped-list section used by the Settings
 * home. Mirrors the chats convo-list pattern (see
 * `feature/chats/impl/ui/ConvoListItem.kt`):
 *
 * - Rows render via [SegmentedListItem], with position-aware corner
 *   shaping from `ListItemDefaults.segmentedShapes(index, count)` —
 *   first row top-rounded, last bottom-rounded, single fully
 *   rounded.
 * - Container tone is `surfaceContainer` so the section reads as one
 *   card against the screen's `surface` background.
 * - The section caption above the card uses `labelMedium` +
 *   `onSurfaceVariant` per the Google Play settings sheet pattern.
 *
 * The Column wrapping the rows uses
 * `Arrangement.spacedBy(ListItemDefaults.SegmentedGap)` — the
 * framework's canonical 2dp baseline for grouped-list rows.
 *
 * Empty sections render nothing — including the caption — so a not-
 * yet-implemented section slot does not show a stray label.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsSection(
    rows: ImmutableList<SettingsRow>,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (rows.isEmpty()) return
    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                SettingsRowRender(row = row, index = index, count = rows.size)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsRowRender(
    row: SettingsRow,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    val colors =
        ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    // Destructive rows (Sign out, Delete account) tint icon + label with `error`.
    // Both Action and the external Link variant can be destructive.
    val destructiveIconTint =
        (row is SettingsRow.Action && row.isDestructive) ||
            (row is SettingsRow.Link && row.isDestructive)
    val iconTint =
        if (destructiveIconTint) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

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

    when (row) {
        is SettingsRow.Action ->
            SegmentedListItem(
                onClick = row.onClick,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                modifier = modifier,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (row.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                )
            }
        is SettingsRow.Toggle ->
            SegmentedListItem(
                onClick = { row.onCheckedChange(!row.checked) },
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                trailingContent = {
                    Switch(
                        checked = row.checked,
                        onCheckedChange = row.onCheckedChange,
                    )
                },
                modifier = modifier,
            ) {
                Text(text = row.label, style = MaterialTheme.typography.bodyLarge)
            }
        is SettingsRow.Picker ->
            SegmentedListItem(
                onClick = row.onClick,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                trailingContent = {
                    Text(
                        text = row.currentValue,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = modifier,
            ) {
                Text(text = row.label, style = MaterialTheme.typography.bodyLarge)
            }
        is SettingsRow.Link ->
            // Link variant is semantically distinct (external destination)
            // but renders identically to Action in v1. Section tasks attach
            // an "open in new" badge once that icon is added to
            // NubecitaIconName (per the curated-icon-set convention in
            // :designsystem/.../NubecitaIconName.kt).
            SegmentedListItem(
                onClick = row.onClick,
                shapes = shapes,
                colors = colors,
                leadingContent = leadingContent,
                supportingContent = supportingContent,
                modifier = modifier,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color =
                        if (row.isDestructive) {
                            MaterialTheme.colorScheme.error
                        } else {
                            Color.Unspecified
                        },
                )
            }
        is SettingsRow.Info ->
            // Non-interactive row — Surface(shape, surfaceContainer) wrapping a
            // non-clickable ListItem. Screen readers announce it as text, not a
            // disabled button. Uses ListItemShapes.shape from segmentedShapes
            // so position-aware corners (first/middle/last/single) match the
            // surrounding interactive rows pixel-for-pixel.
            Surface(
                shape = shapes.shape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = modifier,
            ) {
                ListItem(
                    headlineContent = {
                        Text(text = row.label, style = MaterialTheme.typography.bodyLarge)
                    },
                    supportingContent = supportingContent,
                    leadingContent = leadingContent,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
    }
}
