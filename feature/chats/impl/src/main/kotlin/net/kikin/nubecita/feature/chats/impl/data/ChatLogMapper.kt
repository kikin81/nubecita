package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetLogResponse
import io.github.kikin81.atproto.chat.bsky.convo.GetLogResponseLogsUnion
import io.github.kikin81.atproto.chat.bsky.convo.LogCreateMessage
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/**
 * One create-message event from `chat.bsky.convo.getLog`, flattened to the
 * fields the background DM-poll worker needs (v2, nubecita-1fy.15). Both
 * inbound and outbound creates are surfaced here; the §3 detection step filters
 * `senderDid == viewer` and applies the read-state / cap rules. Deleted
 * creates carry `isDeleted = true` and empty [text].
 *
 * Boundary contract: like [ConvoMapper], this is the only place that touches
 * the `getLog` wire union — downstream sees [ChatLogEvent] with primitive
 * fields.
 */
data class ChatLogEvent(
    val convoId: String,
    val messageId: String,
    val senderDid: String,
    val text: String,
    val isDeleted: Boolean,
    val sentAt: Instant,
)

/**
 * Maps a wire [GetLogResponse] to the domain [ChatLogPage]: keeps only
 * create-message events (other log types — reads, membership, reactions — are
 * irrelevant to notifications and dropped), preserves order, and carries the
 * response cursor through unchanged.
 */
internal fun GetLogResponse.toChatLogPage(): ChatLogPage =
    ChatLogPage(
        events = logs.mapNotNull { it.toChatLogEvent() }.toImmutableList(),
        nextCursor = cursor,
    )

private fun GetLogResponseLogsUnion.toChatLogEvent(): ChatLogEvent? {
    if (this !is LogCreateMessage) return null
    return when (val m = message) {
        is MessageView ->
            ChatLogEvent(
                convoId = convoId,
                messageId = m.id,
                senderDid = m.sender.did.raw,
                text = m.text,
                isDeleted = false,
                sentAt = Instant.parse(m.sentAt.raw),
            )

        is DeletedMessageView ->
            ChatLogEvent(
                convoId = convoId,
                messageId = m.id,
                senderDid = m.sender.did.raw,
                text = "",
                isDeleted = true,
                sentAt = Instant.parse(m.sentAt.raw),
            )

        else -> null // forward-compat Unknown message variant
    }
}
