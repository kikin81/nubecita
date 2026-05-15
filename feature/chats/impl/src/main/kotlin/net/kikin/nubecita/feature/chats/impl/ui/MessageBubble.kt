package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.designsystem.component.PostCardQuotedPost
import net.kikin.nubecita.designsystem.component.PostCardRecordUnavailable
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.R

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
internal fun messageBubbleShape(
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
 */
@Composable
internal fun MessageBubble(
    message: MessageUi,
    runIndex: Int,
    runCount: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
    onQuotedPostTap: (quotedPostUri: String) -> Unit = {},
) {
    val embed = message.embed
    val showTextBubble = message.isDeleted || message.text.isNotEmpty() || embed == null
    Column(
        modifier = modifier.widthIn(max = maxWidth),
        horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start,
    ) {
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
