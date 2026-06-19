package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.time.rememberChatRelativeTimeText
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
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
 * [onClick] / [onLongClick] are resolved by the caller per interaction
 * mode: out of selection mode a tap opens the thread (→ `Chat(did)` push)
 * and a long-press enters selection mode; in selection mode a tap toggles
 * this row's membership. [selected] drives the M3 selected container tone
 * + `selected` semantics — it reflects either the tablet open-thread row
 * or this row's multi-select membership, decided by the caller.
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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    // Long-press a11y action label — describes the gesture's effect ("enter
    // selection"), surfaced to TalkBack via SegmentedListItem.onLongClickLabel.
    val selectLabel = stringResource(R.string.chats_action_select)
    SegmentedListItem(
        // The single-selection overload (vs the plain onClick one): passing
        // [selected] makes the framework drive BOTH the selected container tone
        // and the accessibility semantics (`selected` + RadioButton role), so
        // TalkBack announces an open / multi-selected row as selected. A manual
        // containerColor swap would paint the right pixels but leave the row
        // semantically unselected. The same overload also natively carries
        // [onLongClick] — no combinedClickable retrofit needed for the
        // long-press-to-select gesture.
        selected = selected,
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = selectLabel,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        // segmentedColors() leaves the resting containerColor transparent —
        // press / ripple is the only feedback. Force surfaceContainer so the
        // rows actually look grouped against the Scaffold's `surface` background.
        // Tone choice per Material 3 Expressive's tone-based-surface guidance
        // (m3.material.io/blog/tone-based-surface-color-m3): surfaceContainer
        // is the canonical "list section" tier — one step up from `surface`.
        //
        // selectedContainerColor lifts the open thread (tablet list-detail) to
        // secondaryContainer — the M3 selected-item tone — so it reads as picked
        // against the surrounding rows. [selected] is always false on phones,
        // where the list isn't visible beside a thread.
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        leadingContent = { Avatar(item = item, modifier = Modifier.size(48.dp)) },
        supportingContent = { SubtitleText(item = item) },
        trailingContent = { TrailingMeta(item = item) },
        // Stable tag for the screengrab marketing journey to tap into a DM
        // thread; every row shares it, the journey taps the first match.
        modifier = modifier.testTag("chat_convo_item"),
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
    NubecitaAvatar(
        model = item.avatarUrl,
        contentDescription = null,
        modifier = modifier,
        size = 48.dp,
        fallback =
            avatarFallbackFor(
                did = item.otherUserDid,
                handle = item.otherUserHandle,
                displayName = item.displayName,
            ),
    )
}

@Composable
private fun HeadlineText(item: ConvoListItemUi) {
    // titleMedium (16sp Medium 500) — Material 3 Expressive's recommended emphasis
    // for the primary identifier in a list row. One token bigger + heavier than the
    // standard SegmentedListItem headline default (bodyLarge, 16sp Regular) so the
    // contact name reads as the dominant element, matching the GChat / Google Messages
    // visual rhythm without overshooting into titleLarge territory.
    //
    // Unread rows bump to Bold (700) — the standard chat-inbox "unread = bold"
    // affordance. Muted convos are NOT excluded here: the in-row treatment still
    // reflects unread; only the bottom-nav badge aggregate excludes muted.
    Text(
        text = item.displayName ?: item.otherUserHandle,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (item.unreadCount > 0) FontWeight.Bold else null,
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
private fun TrailingMeta(item: ConvoListItemUi) {
    // Relative timestamp on top, unread-count badge beneath. End-aligned so
    // both hug the row's trailing edge. The badge appears only when unread; a
    // muted convo still shows it (only the bottom-nav aggregate drops muted).
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TrailingTimestamp(item = item)
        if (item.unreadCount > 0) {
            UnreadBadge(count = item.unreadCount)
        }
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    // Visual caps at 99+ (badge real estate); the a11y string uses the true
    // count via the plurals resource so TalkBack reads "5 unread messages".
    val description = pluralStringResource(R.plurals.chats_unread_messages, count, count)
    Badge(
        modifier =
            Modifier
                .padding(top = 2.dp)
                .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(text = if (count > 99) "99+" else count.toString())
    }
}

@Composable
private fun TrailingTimestamp(item: ConvoListItemUi) {
    // Null sentAt = the convo has no messages yet (listConvos surfaces brand-new
    // conversations before any send). Match the legacy empty-string output for
    // that case so the trailing slot remains empty rather than rendering a stale
    // value — the SubtitleText already shows an em-dash to communicate "no
    // messages".
    val sentAt = item.sentAt ?: return
    val label by rememberChatRelativeTimeText(then = sentAt)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}
