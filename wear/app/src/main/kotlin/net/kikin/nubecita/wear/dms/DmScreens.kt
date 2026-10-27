package net.kikin.nubecita.wear.dms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.feed.relativeTime
import net.kikin.nubecita.wear.gate.GateMessageScreen
import net.kikin.nubecita.wear.ui.WearScrollIndicator

/**
 * The synced conversations, most recent first. A conversation with unread
 * messages has a bold title and its count; muted ones never count. Shows the
 * stored conversations whether or not the phone is in reach.
 */
@Composable
fun ConvoListScreen(
    state: WatchDmState,
    nowMs: Long,
    onOpen: (WatchConvoUi) -> Unit,
    modifier: Modifier = Modifier,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    when (state.status) {
        // Brief: the local store answers fast. Plain black, no spinner.
        WatchDmStatus.Loading -> return
        WatchDmStatus.NotSynced -> {
            GateMessageScreen(
                title = stringResource(R.string.wear_dms_title),
                body = stringResource(R.string.wear_dms_not_synced),
                modifier = modifier,
            )
            return
        }
        WatchDmStatus.Ready -> Unit
    }
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        scrollIndicator = { WearScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(stringResource(R.string.wear_dms_title))
                }
            }
            if (state.convos.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.wear_dms_empty),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(state.convos, key = { it.convoId }) { convo ->
                val unread = convo.unreadCount > 0 && !convo.muted
                val unreadLabel = if (unread) pluralStringResource(R.plurals.wear_dms_unread, convo.unreadCount, convo.unreadCount) else null
                Card(
                    onClick = { onOpen(convo) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                convo.title,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (unread) FontWeight.Bold else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (unreadLabel != null) {
                                Text(
                                    convo.unreadCount.toString(),
                                    modifier = Modifier.semantics { contentDescription = unreadLabel },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        convo.lastMessage?.let { last ->
                            Text(
                                previewText(last),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        convo.lastMessageAtMs?.let {
                            Text(
                                relativeTime(it, nowMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One conversation's synced messages, newest at the bottom; the list opens
 * there. The viewer's messages sit on the right, everyone else's on the left
 * (with the sender's name in a group). Plain text items take no
 * `transformedHeight`: that edge morph is for surfaces and crops text.
 */
@Composable
fun ThreadScreen(
    thread: WatchThreadUi,
    nowMs: Long,
    modifier: Modifier = Modifier,
    // Header + messages: anchoring on the last one opens at the newest.
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(initialAnchorItemIndex = thread.messages.size),
) {
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        scrollIndicator = { WearScrollIndicator(listState) },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(ListHeaderDefaults.minimumTopListContentPadding),
                    transformation = SurfaceTransformation(transformationSpec),
                ) {
                    Text(thread.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            items(thread.messages, key = { it.id }) { message -> MessageBubble(message, nowMs) }
        }
    }
}

@Composable
private fun MessageBubble(
    message: WatchMessageUi,
    nowMs: Long,
) {
    val mine = message.fromViewer
    // Inset from the sides: the newest message sits at the bottom of the list,
    // where a round screen is narrowest, and would otherwise be clipped.
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 160.dp)
                    .background(
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        ) {
            val onBubble = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            message.senderName?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                bodyText(message),
                style = MaterialTheme.typography.bodyMedium,
                color = onBubble,
                fontStyle = if (message.isDeleted || message.text.isBlank()) FontStyle.Italic else null,
            )
            Text(relativeTime(message.sentAtMs, nowMs), style = MaterialTheme.typography.labelSmall, color = onBubble.copy(alpha = 0.7f))
        }
    }
}

// The message's text, or what stands in for it.
@Composable
private fun bodyText(message: WatchMessageUi): String =
    when {
        message.isDeleted -> stringResource(R.string.wear_dms_deleted)
        message.text.isBlank() && message.hasAttachment -> stringResource(R.string.wear_dms_attachment)
        else -> message.text
    }

// The list's preview line: "You: …" for the viewer's own last message.
@Composable
private fun previewText(message: WatchMessageUi): String {
    val body = bodyText(message)
    return if (message.fromViewer) stringResource(R.string.wear_dms_you, body) else body
}
