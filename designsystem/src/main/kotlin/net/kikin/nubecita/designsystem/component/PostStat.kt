package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

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
 *
 * Optional [onLongClick] adds a long-press gesture (e.g. share → copy
 * permalink). Only honored on non-toggleable cells; passing it
 * alongside `toggleable = true` is ignored so we don't fight a
 * `Role.Switch`'s own long-press semantics. `combinedClickable` fires
 * the system's long-press haptic automatically — no manual
 * `LocalHapticFeedback` plumbing needed. Pair with [onLongClickLabel]
 * so TalkBack announces what long-press will do (e.g. "Copy link")
 * instead of a generic "press and hold."
 */
@Composable
internal fun PostStat(
    name: NubecitaIconName,
    count: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    active: Boolean = false,
    toggleable: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
) {
    val tint = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    val interactionModifier =
        when {
            toggleable ->
                Modifier.toggleable(
                    value = active,
                    role = Role.Switch,
                    onValueChange = { onClick() },
                )
            onLongClick != null ->
                Modifier.combinedClickable(
                    role = Role.Button,
                    onClickLabel = accessibilityLabel,
                    onLongClickLabel = onLongClickLabel,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            else ->
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
        NubecitaIcon(
            name = name,
            contentDescription = if (toggleable) accessibilityLabel else null,
            filled = filled,
            tint = tint,
            opticalSize = STAT_ICON_SIZE,
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
            PostStat(name = NubecitaIconName.ChatBubble, count = "12", accessibilityLabel = "Reply")
            PostStat(name = NubecitaIconName.Repeat, count = "4", accessibilityLabel = "Repost")
            PostStat(name = NubecitaIconName.Favorite, count = "86", accessibilityLabel = "Like")
            PostStat(name = NubecitaIconName.IosShare, count = "", accessibilityLabel = "Share post")
        }
    }
}

@Preview(name = "PostStat — active like + repost", showBackground = true)
@Composable
private fun PostStatActivePreview() {
    NubecitaTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            PostStat(name = NubecitaIconName.ChatBubble, count = "12", accessibilityLabel = "Reply")
            PostStat(
                name = NubecitaIconName.Repeat,
                count = "5",
                accessibilityLabel = "Undo repost",
                active = true,
                activeColor = MaterialTheme.colorScheme.tertiary,
            )
            PostStat(
                name = NubecitaIconName.Favorite,
                filled = true,
                count = "87",
                accessibilityLabel = "Unlike",
                active = true,
                activeColor = MaterialTheme.colorScheme.secondary,
            )
            PostStat(name = NubecitaIconName.IosShare, count = "", accessibilityLabel = "Share post")
        }
    }
}
