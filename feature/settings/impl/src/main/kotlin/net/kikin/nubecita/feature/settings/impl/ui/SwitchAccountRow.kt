package net.kikin.nubecita.feature.settings.impl.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.settings.impl.R

/**
 * Single-row "Switch account" placeholder section, rendered directly
 * under [SettingsHeader] on the Settings home. Multi-account auth is
 * out of scope for the Settings epic — until it ships, the row is
 * inert and tapping it fires a "Coming soon" snackbar (the
 * `onTap` lambda is the screen's hook to do so).
 *
 * Visually distinct from the surrounding section cards by being its
 * own single-row section: [ListItemDefaults.segmentedShapes] with
 * `count = 1` rounds all four corners. Leading slot shows the
 * signed-in account's avatar; trailing slot is a chevron-style
 * forward-arrow icon, signaling "tap to switch" even though v1's
 * tap is inert.
 *
 * When the multi-account epic lands, swap [onTap]'s wiring from the
 * Coming-soon snackbar to the real account-picker NavKey push;
 * no other call site needs to change.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SwitchAccountRow(
    handle: String,
    did: String,
    displayName: String?,
    avatarUrl: String?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = onTap,
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        leadingContent = {
            NubecitaAvatar(
                model = avatarUrl,
                contentDescription = stringResource(R.string.settings_switch_account_avatar_content_description),
                size = 32.dp,
                fallback = avatarFallbackFor(did = did, handle = handle, displayName = displayName),
                initialTextStyle = MaterialTheme.typography.labelMedium,
            )
        },
        trailingContent = {
            // Decorative: the row itself carries the "Switch account" label.
            // contentDescription = null avoids screen readers announcing
            // the chevron as additional text on top of the row label.
            NubecitaIcon(
                name = NubecitaIconName.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.settings_switch_account_label),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
