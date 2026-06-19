package net.kikin.nubecita.feature.chats.impl.data

import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageSendStatus
import net.kikin.nubecita.feature.chats.impl.MessageUi
import kotlin.time.Clock
import kotlin.time.Instant

internal object BenchChatsMapper {
    fun toConvoListItem(dto: BenchConvoDto): ConvoListItemUi =
        ConvoListItemUi(
            convoId = dto.convoId,
            otherUserDid = dto.otherUserDid,
            otherUserHandle = dto.otherUserHandle,
            displayName = dto.displayName,
            avatarUrl = dto.avatarUrl,
            lastMessageSnippet = dto.lastMessageSnippet,
            lastMessageFromViewer = dto.lastMessageFromViewer,
            lastMessageIsAttachment = dto.lastMessageIsAttachment,
            sentAt = dto.sentAt?.let { parseInstantOrNow(it) },
        )

    fun toMessage(
        dto: BenchMessageDto,
        viewerDid: String,
    ): MessageUi =
        MessageUi(
            id = dto.id,
            senderDid = dto.senderDid,
            isOutgoing = dto.senderDid == viewerDid,
            text = dto.text,
            isDeleted = dto.isDeleted,
            sentAt = parseInstantOrNow(dto.sentAt),
            embed = null,
            sendStatus = MessageSendStatus.Sent,
        )

    private fun parseInstantOrNow(raw: String): Instant =
        runCatching { Instant.parse(raw) }
            .getOrElse {
                Clock.System.now()
            }
}
