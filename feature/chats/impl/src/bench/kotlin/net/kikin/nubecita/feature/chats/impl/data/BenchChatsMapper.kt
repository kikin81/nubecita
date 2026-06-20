package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.MessageSendStatus
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.ReactionUi
import kotlin.time.Clock
import kotlin.time.Instant

internal object BenchChatsMapper {
    fun toConvoListItem(dto: BenchConvoDto): ConvoRowUi =
        if (dto.kind == "group") {
            ConvoRowUi.Group(
                convoId = dto.convoId,
                name = dto.name.orEmpty(),
                members = dto.members.map { it.toAuthorUi() }.toImmutableList(),
                lastMessageSnippet = dto.lastMessageSnippet,
                lastMessageFromViewer = dto.lastMessageFromViewer,
                lastMessageIsAttachment = dto.lastMessageIsAttachment,
                sentAt = dto.sentAt?.let { parseInstantOrNow(it) },
            )
        } else {
            ConvoRowUi.Direct(
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
        }

    private fun BenchMemberDto.toAuthorUi(): AuthorUi =
        AuthorUi(
            did = did,
            handle = handle,
            displayName = displayName?.takeUnless { it.isBlank() } ?: handle,
            avatarUrl = avatarUrl,
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
            reactions =
                dto.reactions
                    .map { ReactionUi(emoji = it.emoji, count = it.count, reactedByViewer = it.reactedByViewer) }
                    .toImmutableList(),
        )

    private fun parseInstantOrNow(raw: String): Instant =
        runCatching { Instant.parse(raw) }
            .getOrElse {
                Clock.System.now()
            }
}
