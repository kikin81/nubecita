package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

internal class FakeChatRepository(
    var nextListResult: Result<ConvoListPage> = Result.success(ConvoListPage(items = persistentListOf())),
    var nextResolveResult: Result<ConvoResolution> =
        Result.success(
            ConvoResolution(
                convoId = "convo-1",
                otherUserHandle = "alice.bsky.social",
                otherUserDisplayName = "Alice",
                otherUserAvatarUrl = null,
                otherUserAvatarHue = 0,
            ),
        ),
    var nextMessagesResult: Result<MessagePage> = Result.success(MessagePage(messages = persistentListOf())),
    var nextSendResult: Result<MessageUi> = Result.success(DEFAULT_SENT_MESSAGE),
) : ChatRepository {
    val listCalls = AtomicInteger(0)
    val resolveCalls = AtomicInteger(0)
    val messagesCalls = AtomicInteger(0)
    val sendCalls = AtomicInteger(0)
    var lastListCursor: String? = null
    var lastResolvedDid: String? = null
    var lastMessagesConvoId: String? = null
    var lastMessagesCursor: String? = null
    var lastSendConvoId: String? = null
    var lastSendText: String? = null

    override suspend fun listConvos(
        cursor: String?,
        limit: Int,
    ): Result<ConvoListPage> {
        listCalls.incrementAndGet()
        lastListCursor = cursor
        return nextListResult
    }

    override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
        resolveCalls.incrementAndGet()
        lastResolvedDid = otherUserDid
        return nextResolveResult
    }

    override suspend fun getMessages(
        convoId: String,
        cursor: String?,
        limit: Int,
    ): Result<MessagePage> {
        messagesCalls.incrementAndGet()
        lastMessagesConvoId = convoId
        lastMessagesCursor = cursor
        return nextMessagesResult
    }

    override suspend fun sendMessage(
        convoId: String,
        text: String,
    ): Result<MessageUi> {
        sendCalls.incrementAndGet()
        lastSendConvoId = convoId
        lastSendText = text
        return nextSendResult
    }

    private companion object {
        val DEFAULT_SENT_MESSAGE =
            MessageUi(
                id = "msg-sent-1",
                senderDid = "did:plc:viewer",
                isOutgoing = true,
                text = "sent",
                isDeleted = false,
                sentAt = Instant.parse("2026-05-01T12:00:00Z"),
            )
    }
}
