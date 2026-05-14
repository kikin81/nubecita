package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
 */
@Composable
internal fun MessageBubble(
    message: MessageUi,
    runIndex: Int,
    runCount: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
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
            modifier
                .widthIn(max = maxWidth)
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
