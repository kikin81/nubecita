package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET

/**
 * Material 3 Expressive segmented convo list row.
 *
 * Rendered via [SegmentedListItem] — the dedicated grouped-list
 * composable that ships with `compose-material3:1.5.0-alpha19+`.
 * Position-aware corner shaping comes from
 * [ListItemDefaults.segmentedShapes]: the first row in a section gets
 * top-rounded corners, middle rows are square, the last row gets
 * bottom-rounded corners, a single row is fully rounded. The container
 * tone comes from [ListItemDefaults.segmentedColors] — a tonally-
 * distinct surface so the group reads as one rounded card.
 *
 * Tapping the row invokes [onTap] with the other-user's DID — the
 * screen's effect collector translates that into a
 * `MainShellNavState.add(Chat(did))` push.
 *
 * Snippet rendering rules (see [SubtitleText]):
 * - `lastMessageSnippet == null` → em-dash.
 * - `lastMessageSnippet == DELETED_MESSAGE_SNIPPET` → italicized
 *   localized "Message deleted" string.
 * - `lastMessageFromViewer == true` → prefix with localized "You: ".
 * - `lastMessageIsAttachment == true` → italicized localized "Sent
 *   an attachment" placeholder (attachment-only is a V2 mapper case
 *   the current schema doesn't reach).
 *
 * @param index 0-based position of this row within its grouped section.
 * @param count Total number of rows in the grouped section. Pair with
 *   [index] so the framework's `segmentedShapes` helper picks the right
 *   first/middle/last/single corner profile.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ConvoListItem(
    item: ConvoListItemUi,
    index: Int,
    count: Int,
    onTap: (otherUserDid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        onClick = { onTap(item.otherUserDid) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // segmentedColors() leaves the resting containerColor transparent —
        // press / ripple is the only feedback. Force surfaceContainer so the
        // rows actually look grouped against the Scaffold's `surface` background.
        // Tone choice per Material 3 Expressive's tone-based-surface guidance
        // (m3.material.io/blog/tone-based-surface-color-m3): surfaceContainer
        // is the canonical "list section" tier — one step up from `surface`.
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        leadingContent = { Avatar(item = item, modifier = Modifier.size(48.dp)) },
        supportingContent = { SubtitleText(item = item) },
        trailingContent = { TrailingTimestamp(item = item) },
        modifier = modifier,
    ) {
        // Trailing `content` lambda is the headline slot — same convention as
        // ListItem / SegmentedListItem in compose-material3 alpha19. The named
        // slots above are for the surrounding leading/supporting/trailing
        // content; the row's primary line lives here.
        HeadlineText(item = item)
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
private fun HeadlineText(item: ConvoListItemUi) {
    // titleMedium (16sp Medium 500) — Material 3 Expressive's recommended emphasis
    // for the primary identifier in a list row. One token bigger + heavier than the
    // standard SegmentedListItem headline default (bodyLarge, 16sp Regular) so the
    // contact name reads as the dominant element, matching the GChat / Google Messages
    // visual rhythm without overshooting into titleLarge territory.
    Text(
        text = item.displayName ?: item.otherUserHandle,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SubtitleText(item: ConvoListItemUi) {
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
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TrailingTimestamp(item: ConvoListItemUi) {
    Text(
        text = item.timestampRelative,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
