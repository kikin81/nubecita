package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi

/**
 * Reflect a just-sent [message] in the cached convo list: update the matching
 * convo's last-message preview/timestamp (as an outgoing message) and move it to
 * the top, so the inbox shows the send live without a refetch.
 *
 * Pure function over the cache snapshot — the single source of truth behind the
 * inbox-freshness fix, unit-tested directly:
 * - `null` cache (inbox never loaded) → `null` (no-op).
 * - convo not in the list → returned unchanged (the next refresh surfaces it).
 * - convo present → patched copy hoisted to index 0, others kept in order.
 */
internal fun patchConvosOnSend(
    current: ImmutableList<ConvoListItemUi>?,
    convoId: String,
    message: MessageUi,
): ImmutableList<ConvoListItemUi>? {
    if (current == null) return null
    val index = current.indexOfFirst { it.convoId == convoId }
    if (index < 0) return current
    val patched =
        current[index].copy(
            lastMessageSnippet = message.text,
            lastMessageFromViewer = true,
            lastMessageIsAttachment = false,
            sentAt = message.sentAt,
        )
    return (listOf(patched) + current.filterIndexed { i, _ -> i != index }).toImmutableList()
}
