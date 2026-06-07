package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnRead
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

internal class FakeChatRepository(
    var nextRefreshResult: Result<ImmutableList<ConvoListItemUi>> = Result.success(persistentListOf()),
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
    /**
     * Optional gate: when set, `sendMessage` suspends on it before returning,
     * so a test can observe the optimistic `Sending` row before reconcile.
     */
    var sendGate: CompletableDeferred<Unit>? = null
    val refreshCalls = AtomicInteger(0)
    val resolveCalls = AtomicInteger(0)
    val messagesCalls = AtomicInteger(0)
    val sendCalls = AtomicInteger(0)
    val markReadCalls = AtomicInteger(0)
    var lastResolvedDid: String? = null
    var lastMessagesConvoId: String? = null
    var lastMessagesCursor: String? = null
    var lastSendConvoId: String? = null
    var lastSendText: String? = null
    var lastMarkReadConvoId: String? = null
    var nextMarkReadResult: Result<Unit> = Result.success(Unit)

    private val convos = MutableStateFlow<ImmutableList<ConvoListItemUi>?>(null)

    override fun observeConvos(): StateFlow<ImmutableList<ConvoListItemUi>?> = convos.asStateFlow()

    override suspend fun refreshConvos(): Result<Unit> {
        refreshCalls.incrementAndGet()
        return nextRefreshResult.map { items -> convos.value = items }
    }

    /** Drive the cache directly (simulating a [sendMessage] patch or an external update). */
    fun emitConvos(items: ImmutableList<ConvoListItemUi>?) {
        convos.value = items
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
        sendGate?.await()
        return nextSendResult
    }

    override suspend fun markConvoRead(convoId: String): Result<Unit> {
        markReadCalls.incrementAndGet()
        lastMarkReadConvoId = convoId
        return nextMarkReadResult.onSuccess {
            convos.value = patchConvosOnRead(convos.value, convoId)
        }
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
