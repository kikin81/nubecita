package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.app.bsky.embed.RecordView
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesResponseMessagesUnion
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewEmbedUnion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.feedmapping.toRecordOrUnavailable
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.feature.chats.impl.MessageUi
import kotlin.time.Instant

/**
 * Maps the wire `chat.bsky.convo.getMessages` response messages to [MessageUi].
 *
 * - `MessageView` → normal `MessageUi`, `isDeleted = false`, `embed` populated
 *   when the wire `embed` is a `RecordView` (resolved or unavailable).
 * - `DeletedMessageView` → placeholder `MessageUi`, `isDeleted = true`, empty `text`,
 *   `embed = null`.
 * - All other union variants (`SystemMessageView`, forward-compat unknown) → filtered.
 *   System messages aren't conversational; rendering them is out of MVP scope.
 *
 * Order is preserved; the lexicon returns newest-first.
 */
internal fun List<GetMessagesResponseMessagesUnion>.toMessageUis(viewerDid: String): ImmutableList<MessageUi> {
    if (isEmpty()) return persistentListOf()
    return mapNotNull { union ->
        when (union) {
            is MessageView ->
                MessageUi(
                    id = union.id,
                    senderDid = union.sender.did.raw,
                    isOutgoing = union.sender.did.raw == viewerDid,
                    text = union.text,
                    isDeleted = false,
                    sentAt = Instant.parse(union.sentAt.raw),
                    embed = union.embed.toMessageEmbedUi(),
                )

            is DeletedMessageView ->
                MessageUi(
                    id = union.id,
                    senderDid = union.sender.did.raw,
                    isOutgoing = union.sender.did.raw == viewerDid,
                    text = "",
                    isDeleted = true,
                    sentAt = Instant.parse(union.sentAt.raw),
                )

            else -> null // SystemMessageView + unknown forward-compat variants
        }
    }.toImmutableList()
}

/**
 * Maps `MessageView.embed` to [EmbedUi.RecordOrUnavailable]. The chat
 * lexicon's `messageViewEmbedUnion` only admits `app.bsky.embed.record#view`,
 * so the only meaningful mapping is `RecordView → toRecordOrUnavailable()`.
 * Any forward-compat `Unknown` open-union member drops to `null` — the
 * sender's intent isn't recoverable as a record-shape and a "Quoted post
 * unavailable" chip would mis-state the situation.
 */
private fun MessageViewEmbedUnion?.toMessageEmbedUi(): EmbedUi.RecordOrUnavailable? =
    when (this) {
        null -> null
        is RecordView -> toRecordOrUnavailable()
        else -> null // MessageViewEmbedUnion.Unknown + forward-compat variants
    }
