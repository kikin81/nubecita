package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * One interactive icon + count cell in a [PostCard]'s action row.
 *
 * `active` flips the icon and count to [activeColor] (typically
 * `MaterialTheme.colorScheme.secondary` for like, `tertiary` for repost).
 * Inactive uses `onSurfaceVariant`. The cell is a `Row` clipped to a
 * circle so the ripple matches the icon's affordance.
 *
 * Pass an empty `count` string for cells that don't show a number (e.g.
 * the share button).
 *
 * `toggleable` selects the a11y semantics:
 * - `false` (default) — one-shot action (reply, share). Uses
 *   `Modifier.clickable(role = Role.Button, onClickLabel = accessibilityLabel)`.
 *   TalkBack announces "Double-tap to <label>".
 * - `true` — on/off toggle (like, repost). Uses
 *   `Modifier.toggleable(value = active, role = Role.Switch)` and sets the
 *   Icon's `contentDescription = accessibilityLabel`. TalkBack announces
 *   "<label>, switch, <on|off>, double tap to toggle" so the user gets BOTH
 *   the action and the current state — the implicit-state-via-action-verb
 *   pattern (e.g. "Unlike") was insufficient because it omitted on/off.
 */
@Composable
internal fun PostStat(
    icon: ImageVector,
    count: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    active: Boolean = false,
    toggleable: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionModifier =
        if (toggleable) {
            Modifier.toggleable(
                value = active,
                role = Role.Switch,
                onValueChange = { onClick() },
            )
        } else {
            Modifier.clickable(
                role = Role.Button,
                onClickLabel = accessibilityLabel,
                onClick = onClick,
            )
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .clip(CircleShape)
                .then(interactionModifier)
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        // For toggleable cells, the Icon carries the contentDescription so
        // TalkBack has a noun to attach to the "switch, on/off" announcement.
        // For non-toggleable cells, the action verb comes via clickable's
        // onClickLabel above and the Icon stays decorative — avoids double-
        // announcement.
        Icon(
            imageVector = icon,
            contentDescription = if (toggleable) accessibilityLabel else null,
            tint = tint,
            modifier = Modifier.size(STAT_ICON_SIZE),
        )
        if (count.isNotEmpty()) {
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

private val STAT_ICON_SIZE = 18.dp

@Preview(name = "PostStat — inactive", showBackground = true)
@Composable
private fun PostStatInactivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(icon = Icons.Outlined.ChatBubbleOutline, count = "12", accessibilityLabel = "Reply")
            PostStat(icon = Icons.Outlined.Repeat, count = "4", accessibilityLabel = "Repost")
            PostStat(icon = Icons.Outlined.FavoriteBorder, count = "86", accessibilityLabel = "Like")
            PostStat(icon = Icons.Outlined.IosShare, count = "", accessibilityLabel = "Share post")
        }
    }
}

@Preview(name = "PostStat — active like + repost", showBackground = true)
@Composable
private fun PostStatActivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(icon = Icons.Outlined.ChatBubbleOutline, count = "12", accessibilityLabel = "Reply")
            PostStat(
                icon = Icons.Outlined.Repeat,
                count = "5",
                accessibilityLabel = "Undo repost",
                active = true,
                activeColor = MaterialTheme.colorScheme.tertiary,
            )
            PostStat(
                icon = Icons.Filled.Favorite,
                count = "87",
                accessibilityLabel = "Unlike",
                active = true,
                activeColor = MaterialTheme.colorScheme.secondary,
            )
            PostStat(icon = Icons.Outlined.IosShare, count = "", accessibilityLabel = "Share post")
        }
    }
}
