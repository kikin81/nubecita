package net.kikin.nubecita.feature.postdetail.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.data.models.ThreadItem

/** Whether a thread row's line runs in from the row [above] and out to the row [below]. */
@Immutable
internal data class ConnectorFlags(
    val above: Boolean,
    val below: Boolean,
) {
    companion object {
        val None = ConnectorFlags(above = false, below = false)
    }
}

/**
 * The thread line for every row, in the same order as the rows.
 *
 * A line joins two neighbouring rows when the lower one is the upper one's
 * direct reply, which the flattened list makes a local question:
 *
 * - above the opened post, every row is the parent of the next, so each
 *   ancestor joins the row under it, down into the opened post;
 * - **nothing leaves the opened post.** It is drawn full width with no gutter
 *   to carry a line, and its replies start fresh beneath it;
 * - below it, a reply joins the next row only when that row is one level
 *   deeper (its own reply) or its own "read more replies" row. A sibling, or
 *   the next conversation, is not joined.
 *
 * A line is the pair of flags on either side of a gap, so `above` is never
 * decided on its own: a row is joined from above exactly when the row over it
 * joins downward. That is what keeps a line from being drawn into, or out of,
 * a blocked or missing post.
 */
internal fun List<ThreadItem>.connectorFlags(): List<ConnectorFlags> {
    val below = List(size) { index -> joinsDownward(this[index], getOrNull(index + 1)) }
    return List(size) { index -> ConnectorFlags(above = index > 0 && below[index - 1], below = below[index]) }
}

private fun joinsDownward(
    row: ThreadItem,
    next: ThreadItem?,
): Boolean =
    when (row) {
        is ThreadItem.MoreParents -> next is ThreadItem.Ancestor
        is ThreadItem.Ancestor -> next is ThreadItem.Ancestor || next is ThreadItem.Focus
        is ThreadItem.Reply ->
            when (next) {
                is ThreadItem.Reply -> next.depth == row.depth + 1
                is ThreadItem.MoreReplies -> next.postUri == row.post.id
                else -> false
            }
        is ThreadItem.Focus,
        is ThreadItem.MoreReplies,
        ThreadItem.OtherReplies,
        is ThreadItem.Blocked,
        is ThreadItem.NotFound,
        -> false
    }
