package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.designsystem.component.PostCardQuotedPost
import net.kikin.nubecita.designsystem.component.PostCardRecordUnavailable
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.MessageSendStatus
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.ReactionUi
import net.kikin.nubecita.feature.chats.impl.RepliedMessageUi

// How far the reaction chips ride up over the message body's bottom edge (matching
// the official app) instead of floating in a gap beneath it. Negative — see
// [ReactionOverlapLayout], which folds this into the container's measured height so
// no dead space is reserved below the chips.
private val ReactionOverlap = (-20).dp

/**
 * Asymmetric M3 Expressive bubble shape for a message at [index] in a run of
 * [count] consecutive same-sender messages. Mirrors `ListItemDefaults.segmentedShapes`
 * structurally but applies segmented (small) corners ONLY on the sender side —
 * the opposite side stays fully rounded.
 *
 *  count == 1                    → all 16dp (fully rounded pill).
 *  index == 0, count > 1         → outer top corners 16dp, inner bottom-tail 4dp.
 *  1..count-2                    → both tail-side corners 4dp.
 *  index == count-1, count > 1   → outer bottom corners 16dp, inner top-tail 4dp.
 */
fun messageBubbleShape(
    index: Int,
    count: Int,
    isOutgoing: Boolean,
): Shape {
    val large = 16.dp
    val small = 4.dp
    val isFirst = index == 0
    val isLast = index == count - 1
    val isSingle = count == 1

    val topSender = if (isFirst || isSingle) large else small
    val bottomSender = if (isLast || isSingle) large else small

    return if (isOutgoing) {
        RoundedCornerShape(
            topStart = large,
            topEnd = topSender,
            bottomEnd = bottomSender,
            bottomStart = large,
        )
    } else {
        RoundedCornerShape(
            topStart = topSender,
            topEnd = large,
            bottomEnd = large,
            bottomStart = bottomSender,
        )
    }
}

/**
 * A single message bubble. Container color, content color, and shape are derived
 * from [isOutgoing] + the run position; rendered text is the message body
 * (italicised placeholder when [MessageUi.isDeleted]).
 *
 * When [MessageUi.embed] is non-null, an embed card is stacked under the
 * text bubble (same horizontal alignment as the bubble). For an
 * embed-only message (empty `text`, non-null `embed`), the text bubble
 * is omitted — rendering an empty bubble would just be visual noise.
 * Deleted messages (`isDeleted = true`) always carry a null `embed` per
 * the mapper, so they render only the italic placeholder.
 *
 * Outgoing rows mid-send carry a [MessageSendStatus] footer under the
 * bubble: a `Sending` spinner, or a `Failed` "Not delivered" line with an
 * inline retry affordance ([onRetrySend], keyed on the row's temp id).
 * Incoming and `Sent` rows render no footer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MessageBubble(
    message: MessageUi,
    runIndex: Int,
    runCount: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
    onQuotedPostTap: (quotedPostUri: String) -> Unit = {},
    onRetrySend: (tempId: String) -> Unit = {},
    onReactionToggle: (emoji: String) -> Unit = {},
) {
    Column(
        modifier = modifier.widthIn(max = maxWidth),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
    ) {
        // Reactions overlap the body's bottom edge via a custom layout (see
        // [ReactionOverlapLayout]); without them the body renders on its own.
        if (message.reactions.isNotEmpty()) {
            ReactionOverlapLayout(
                isOutgoing = message.isOutgoing,
                body = {
                    MessageBubbleBody(
                        message = message,
                        runIndex = runIndex,
                        runCount = runCount,
                        onQuotedPostTap = onQuotedPostTap,
                    )
                },
                reactions = {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.reactions.forEach { reaction ->
                            ReactionChip(reaction = reaction, onClick = { onReactionToggle(reaction.emoji) })
                        }
                    }
                },
            )
        } else {
            MessageBubbleBody(
                message = message,
                runIndex = runIndex,
                runCount = runCount,
                onQuotedPostTap = onQuotedPostTap,
            )
        }
        // Send-status footer for outgoing rows only; server-fetched + reconciled
        // messages are Sent and render nothing.
        if (message.isOutgoing) {
            when (message.sendStatus) {
                MessageSendStatus.Sending -> SendingFooter()
                MessageSendStatus.Failed -> FailedFooter(onRetry = { onRetrySend(message.id) })
                MessageSendStatus.Sent -> Unit
            }
        }
    }
}

/**
 * The message's visual body: the text bubble and/or the embed card, stacked and
 * aligned to the sender side. Reactions (when present) ride up onto this unit's
 * bottom edge via [ReactionOverlapLayout]. Emits a single layout node (the wrapping
 * Column) so the overlap layout can treat it as one measurable.
 */
@Composable
private fun MessageBubbleBody(
    message: MessageUi,
    runIndex: Int,
    runCount: Int,
    onQuotedPostTap: (quotedPostUri: String) -> Unit,
) {
    val embed = message.embed
    val showTextBubble = message.isDeleted || message.text.isNotEmpty() || embed == null
    Column(
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
    ) {
        val replyTo = message.replyTo
        if (replyTo != null) {
            RepliedMessagePreview(reply = replyTo)
            Spacer(Modifier.height(2.dp))
        }
        if (showTextBubble) {
            MessageTextBubble(
                message = message,
                runIndex = runIndex,
                runCount = runCount,
            )
        }
        if (embed != null) {
            if (showTextBubble) Spacer(Modifier.height(4.dp))
            MessageEmbedCard(embed = embed, onQuotedPostTap = onQuotedPostTap)
        }
    }
}

/**
 * Compact quote of the message being replied to, shown stacked just above the
 * message bubble (like the official app). A leading accent bar + a recessed
 * `surfaceContainerLow` inset (per the surface-roles contract) with a one-line
 * snippet; a `isFromViewer` quote is labelled "You". A deleted target shows the
 * italic deleted placeholder instead of body text.
 */
@Composable
private fun RepliedMessagePreview(
    reply: RepliedMessageUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary),
        )
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (reply.isFromViewer) {
                Text(
                    text = stringResource(R.string.chat_reply_you),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            if (reply.isDeleted) {
                Text(
                    text = stringResource(R.string.chats_row_deleted_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = reply.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Stacks [body] with a [reactions] row that overlaps up onto the body's bottom edge
 * by [ReactionOverlap] (matching the official app).
 *
 * Unlike `Modifier.offset`, this reports the true, overlap-reduced height so the
 * parent Column reserves no empty space below the chips. The reactions ride up to
 * `body.height + overlap` (overlap negative), clamped to `>= 0` so a body shorter
 * than the overlap never forces a negative — out-of-bounds — placement (which the
 * Layoutlib screenshot host would clip). The final size is coerced back into the
 * incoming constraints, and horizontal placement uses that coerced width (via
 * `placeRelative`, so it mirrors under RTL) — outgoing content pinned to the end
 * edge, incoming to the start — even under a min-width parent. Each slot is wrapped
 * in a `Box` so it is always exactly one measurable.
 */
@Composable
private fun ReactionOverlapLayout(
    isOutgoing: Boolean,
    body: @Composable () -> Unit,
    reactions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { body() }
            Box { reactions() }
        },
    ) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val bodyPlaceable = measurables[0].measure(childConstraints)
        val reactionsPlaceable = measurables[1].measure(childConstraints)
        val overlapPx = ReactionOverlap.roundToPx() // negative → pulls the row up
        // Never negative: a body shorter than the overlap would otherwise place the
        // row out of bounds and get clipped by the screenshot host.
        val reactionsY = (bodyPlaceable.height + overlapPx).coerceAtLeast(0)
        val width =
            maxOf(bodyPlaceable.width, reactionsPlaceable.width)
                .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height =
            maxOf(bodyPlaceable.height, reactionsY + reactionsPlaceable.height)
                .coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            // Offsets are expressed from the start edge (0 = start, width - childWidth
            // = end); placeRelative mirrors them under RTL. isOutgoing → align to the
            // end edge, incoming → start.
            val bodyX = if (isOutgoing) width - bodyPlaceable.width else 0
            val reactionsX = if (isOutgoing) width - reactionsPlaceable.width else 0
            bodyPlaceable.placeRelative(bodyX, 0)
            reactionsPlaceable.placeRelative(reactionsX, reactionsY)
        }
    }
}

@Composable
private fun ReactionChip(
    reaction: ReactionUi,
    onClick: () -> Unit,
) {
    // Viewer's own reactions use the M3 selected tone; others use the raised-affordance
    // surfaceContainerHigh (per the surface-roles contract).
    val container =
        if (reaction.reactedByViewer) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val content =
        if (reaction.reactedByViewer) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = "${reaction.emoji} ${reaction.count}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** A small spinner + "Sending" label shown under an in-flight outgoing bubble. */
@Composable
private fun SendingFooter() {
    Row(
        modifier = Modifier.padding(top = 2.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // nubecita-allow-raw-progress: 10.dp inline "Sending" dot, below the
        // ~16.dp floor where the wavy ring's waves can render.
        CircularProgressIndicator(
            modifier = Modifier.size(10.dp),
            strokeWidth = 1.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.chat_send_status_sending),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * "Not delivered" line with an inline Retry button shown under a failed
 * outgoing bubble. The error icon + label use the error color; the retry
 * is a low-emphasis text button so the bubble stays the visual anchor.
 */
@Composable
private fun FailedFooter(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Error,
            contentDescription = null,
            filled = true,
            opticalSize = 14.dp,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = stringResource(R.string.chat_send_status_failed),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(
            onClick = onRetry,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.chat_send_retry),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MessageTextBubble(
    message: MessageUi,
    runIndex: Int,
    runCount: Int,
) {
    val shape = messageBubbleShape(index = runIndex, count = runCount, isOutgoing = message.isOutgoing)
    val containerColor =
        if (message.isOutgoing) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    val contentColor =
        if (message.isOutgoing) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (message.isDeleted) {
            Text(
                text = stringResource(R.string.chats_row_deleted_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = contentColor,
            )
        } else {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun MessageEmbedCard(
    embed: EmbedUi.RecordOrUnavailable,
    onQuotedPostTap: (quotedPostUri: String) -> Unit,
) {
    when (embed) {
        is EmbedUi.Record ->
            PostCardQuotedPost(
                quotedPost = embed.quotedPost,
                onTap = { onQuotedPostTap(embed.quotedPost.uri) },
            )
        // RecordUnavailable stays inert — the target is gone, no destination
        // to navigate to.
        is EmbedUi.RecordUnavailable -> PostCardRecordUnavailable(reason = embed.reason)
    }
}
