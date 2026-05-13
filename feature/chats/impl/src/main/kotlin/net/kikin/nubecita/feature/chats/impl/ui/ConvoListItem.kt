package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET

/**
 * GChat-style convo list row.
 *
 * 64dp minimum height for touch target. Tapping the row invokes
 * [onTap] with the other-user's DID — the screen's effect collector
 * translates that into a `MainShellNavState.add(Chat(did))` push.
 *
 * Snippet rendering rules:
 * - `lastMessageSnippet == null` → em-dash.
 * - `lastMessageSnippet == DELETED_MESSAGE_SNIPPET` → italicized
 *   localized "Message deleted" string.
 * - `lastMessageFromViewer == true` → prefix with localized "You: ".
 * - `lastMessageIsAttachment == true` → italicized localized "Sent
 *   an attachment" placeholder (attachment-only is a V2 mapper case
 *   the current schema doesn't reach).
 */
@Composable
internal fun ConvoListItem(
    item: ConvoListItemUi,
    onTap: (otherUserDid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clickable(
                    role = Role.Button,
                    onClick = { onTap(item.otherUserDid) },
                ).heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item = item, modifier = Modifier.size(40.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TitleRow(item = item)
            SubtitleLine(item = item)
        }
    }
}

@Composable
private fun Avatar(
    item: ConvoListItemUi,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color.hsv(item.avatarHue.toFloat(), saturation = 0.5f, value = 0.55f)
    val initials =
        (item.displayName ?: item.otherUserHandle)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercase()
            ?: "?"
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(hueColor),
        contentAlignment = Alignment.Center,
    ) {
        if (item.avatarUrl != null) {
            NubecitaAsyncImage(
                model = item.avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TitleRow(item: ConvoListItemUi) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.displayName ?: item.otherUserHandle,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.timestampRelative,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubtitleLine(item: ConvoListItemUi) {
    val snippet = item.lastMessageSnippet
    val (text, italic) =
        when {
            snippet == null -> "—" to false
            snippet == DELETED_MESSAGE_SNIPPET -> stringResource(R.string.chats_row_deleted_placeholder) to true
            item.lastMessageIsAttachment -> stringResource(R.string.chats_row_attachment_placeholder) to true
            item.lastMessageFromViewer -> stringResource(R.string.chats_row_you_prefix, snippet) to false
            else -> snippet to false
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
