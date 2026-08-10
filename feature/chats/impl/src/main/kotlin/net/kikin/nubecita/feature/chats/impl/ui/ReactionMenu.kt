package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.R

/** Six common quick-react emoji shown in the long-press menu. */
internal val QUICK_REACTIONS = persistentListOf("❤️", "😂", "👍", "😮", "😢", "🙏")

@Composable
internal fun ReactionMenu(
    onPick: (emoji: String) -> Unit,
    onMore: () -> Unit,
    onReply: () -> Unit,
    onDismiss: () -> Unit,
    // Null when there is nothing to copy — a deleted message, or one whose only
    // content is a quoted-post embed. An always-present Copy that silently does
    // nothing is worse than no Copy at all.
    onCopy: (() -> Unit)? = null,
) {
    Popup(
        alignment = Alignment.Center,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            // Constrain to the reaction row's intrinsic width so the reply Row's
            // fillMaxWidth() fills the menu — not the whole screen (Popup content is
            // measured with unbounded width).
            Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QUICK_REACTIONS.forEach { emoji ->
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineSmall,
                            // minimumInteractiveComponentSize first so the clickable area is the
                            // enlarged ≥48dp box (Material/WCAG min touch target) — Modifier.clickable
                            // doesn't apply it automatically the way material buttons do.
                            modifier =
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(CircleShape)
                                    .clickable { onPick(emoji) }
                                    .padding(6.dp),
                        )
                    }
                    IconButton(onClick = onMore) {
                        NubecitaIcon(
                            name = NubecitaIconName.Add,
                            contentDescription = stringResource(R.string.chat_react_more),
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // Copy lives HERE rather than on a long-press of its own: the
                // bubble's long-press already opens this menu, so a second
                // long-press gesture would have to fight it (nubecita-io24.3).
                // The bottom rounding belongs to whichever row is last.
                MenuActionRow(
                    icon = NubecitaIconName.Reply,
                    label = stringResource(R.string.chat_reply_action),
                    isLast = onCopy == null,
                    onClick = onReply,
                )
                if (onCopy != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    MenuActionRow(
                        icon = NubecitaIconName.ContentCopy,
                        label = stringResource(R.string.chat_copy_action),
                        isLast = true,
                        onClick = onCopy,
                    )
                }
            }
        }
    }
}

/**
 * One full-width action row in the long-press menu. [isLast] carries the
 * bottom corner rounding, which has to follow the final row rather than being
 * pinned to Reply — otherwise adding Copy leaves a square corner over the
 * Surface's rounded one.
 */
@Composable
private fun MenuActionRow(
    icon: NubecitaIconName,
    label: String,
    isLast: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(
                    if (isLast) {
                        RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
                    } else {
                        RoundedCornerShape(0.dp)
                    },
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaIcon(name = icon, contentDescription = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
