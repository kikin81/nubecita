package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnAccept
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnLeave
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnMute
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
    var nextRequestRefreshResult: Result<ImmutableList<ConvoListItemUi>> = Result.success(persistentListOf()),
) : ChatRepository {
    /**
     * Optional gate: when set, `sendMessage` suspends on it before returning,
     * so a test can observe the optimistic `Sending` row before reconcile.
     */
    var sendGate: CompletableDeferred<Unit>? = null

    /**
     * Optional gate: when set, `markConvoRead` suspends on it, so a test can
     * verify the load job releases its single-flight guard (and a manual
     * refresh proceeds) while mark-read is still in flight.
     */
    var markReadGate: CompletableDeferred<Unit>? = null
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
    val getLogCalls = AtomicInteger(0)
    var lastGetLogCursor: String? = null
    var nextGetLogResult: Result<ChatLogPage> = Result.success(ChatLogPage())
    val refreshRequestCalls = AtomicInteger(0)

    private val convos = MutableStateFlow<ImmutableList<ConvoListItemUi>?>(null)
    private val requestConvos = MutableStateFlow<ImmutableList<ConvoListItemUi>?>(null)

    override fun observeConvos(): StateFlow<ImmutableList<ConvoListItemUi>?> = convos.asStateFlow()

    override fun observeRequestConvos(): StateFlow<ImmutableList<ConvoListItemUi>?> = requestConvos.asStateFlow()

    override suspend fun refreshConvos(): Result<Unit> {
        refreshCalls.incrementAndGet()
        return nextRefreshResult.map { items -> convos.value = items }
    }

    override suspend fun refreshRequestConvos(): Result<Unit> {
        refreshRequestCalls.incrementAndGet()
        return nextRequestRefreshResult.map { items -> requestConvos.value = items }
    }

    val leaveCalls = mutableListOf<String>()
    val acceptCalls = mutableListOf<String>()
    val setMutedCalls = mutableListOf<Pair<String, Boolean>>()
    var nextLeaveResult: Result<Unit> = Result.success(Unit)
    var nextAcceptResult: Result<Unit> = Result.success(Unit)
    var nextSetMutedResult: Result<Unit> = Result.success(Unit)

    override suspend fun leaveConvo(convoId: String): Result<Unit> {
        leaveCalls += convoId
        return nextLeaveResult.onSuccess {
            convos.value = patchConvosOnLeave(convos.value, convoId)
            requestConvos.value = patchConvosOnLeave(requestConvos.value, convoId)
        }
    }

    override suspend fun acceptConvo(convoId: String): Result<Unit> {
        acceptCalls += convoId
        return nextAcceptResult.onSuccess {
            val (accepted, requests) = patchConvosOnAccept(convos.value, requestConvos.value, convoId)
            convos.value = accepted
            requestConvos.value = requests
        }
    }

    override suspend fun setMuted(
        convoId: String,
        muted: Boolean,
    ): Result<Unit> {
        setMutedCalls += convoId to muted
        return nextSetMutedResult.onSuccess {
            convos.value = patchConvosOnMute(convos.value, convoId, muted)
            requestConvos.value = patchConvosOnMute(requestConvos.value, convoId, muted)
        }
    }

    /** Drive the cache directly (simulating a [sendMessage] patch or an external update). */
    fun emitConvos(items: ImmutableList<ConvoListItemUi>?) {
        convos.value = items
    }

    /** Drive the request cache directly (simulating an external update). */
    fun emitRequestConvos(items: ImmutableList<ConvoListItemUi>?) {
        requestConvos.value = items
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
        markReadGate?.await()
        return nextMarkReadResult.onSuccess {
            convos.value = patchConvosOnRead(convos.value, convoId)
        }
    }

    override suspend fun getLog(cursor: String?): Result<ChatLogPage> {
        getLogCalls.incrementAndGet()
        lastGetLogCursor = cursor
        return nextGetLogResult
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
