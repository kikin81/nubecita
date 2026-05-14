package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

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
