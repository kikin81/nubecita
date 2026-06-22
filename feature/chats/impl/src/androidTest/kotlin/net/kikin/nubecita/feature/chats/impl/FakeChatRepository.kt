package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.feature.chats.impl.data.ChatConvo
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.JoinRequestPage
import net.kikin.nubecita.feature.chats.impl.data.MemberPage
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

/**
 * androidTest-classpath copy of the unit-test `FakeChatRepository`.
 *
 * The unit-test source set (`src/test/`) is not on the androidTest
 * compile classpath, so the fake is duplicated here. If the
 * `ChatRepository` interface grows new methods, both copies need to
 * be updated.
 */
internal class FakeChatRepository(
    var nextRefreshResult: Result<ImmutableList<ConvoRowUi>> = Result.success(persistentListOf()),
    var nextResolveResult: Result<ConvoResolution> =
        Result.success(
            ConvoResolution(
                convoId = "convo-1",
                otherUserHandle = "alice.bsky.social",
                otherUserDisplayName = "Alice",
                otherUserAvatarUrl = null,
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
    var lastResolvedDid: String? = null
    var lastMessagesConvoId: String? = null
    var lastMessagesCursor: String? = null
    var lastSendConvoId: String? = null
    var lastSendText: String? = null
    var lastMarkReadConvoId: String? = null

    private val convos = MutableStateFlow<ImmutableList<ConvoRowUi>?>(null)
    private val requestConvos = MutableStateFlow<ImmutableList<ConvoRowUi>?>(persistentListOf())

    override fun observeConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = convos.asStateFlow()

    override fun observeRequestConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = requestConvos.asStateFlow()

    override suspend fun refreshConvos(): Result<Unit> {
        refreshCalls.incrementAndGet()
        return nextRefreshResult.map { items -> convos.value = items }
    }

    override suspend fun refreshRequestConvos(): Result<Unit> = Result.success(Unit)

    val leaveCalls = AtomicInteger(0)
    val acceptCalls = AtomicInteger(0)
    val setMutedCalls = AtomicInteger(0)

    override suspend fun leaveConvo(convoId: String): Result<Unit> {
        leaveCalls.incrementAndGet()
        return Result.success(Unit)
    }

    override suspend fun acceptConvo(convoId: String): Result<Unit> {
        acceptCalls.incrementAndGet()
        return Result.success(Unit)
    }

    override suspend fun setMuted(
        convoId: String,
        muted: Boolean,
    ): Result<Unit> {
        setMutedCalls.incrementAndGet()
        return Result.success(Unit)
    }

    /** Drive the request cache directly (simulating an external update). */
    fun emitRequestConvos(items: ImmutableList<ConvoRowUi>?) {
        requestConvos.value = items
    }

    /** Drive the cache directly (simulating a [sendMessage] patch or an external update). */
    fun emitConvos(items: ImmutableList<ConvoRowUi>?) {
        convos.value = items
    }

    override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
        resolveCalls.incrementAndGet()
        lastResolvedDid = otherUserDid
        return nextResolveResult
    }

    override suspend fun getConvo(convoId: String): Result<ChatConvo> =
        Result.success(
            ChatConvo(
                convoId = convoId,
                header =
                    ChatHeader.Direct(
                        did = "",
                        handle = "",
                        displayName = null,
                        avatarUrl = null,
                    ),
                canPost = true,
            ),
        )

    override suspend fun getProfiles(dids: List<String>): Result<List<net.kikin.nubecita.data.models.AuthorUi>> = Result.success(emptyList())

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
        lastMarkReadConvoId = convoId
        return Result.success(Unit)
    }

    override suspend fun addReaction(
        convoId: String,
        messageId: String,
        emoji: String,
    ): Result<MessageUi> =
        Result.success(
            MessageUi(
                id = messageId,
                senderDid = "did:plc:viewer",
                isOutgoing = true,
                text = "",
                isDeleted = false,
                sentAt = Instant.parse("2026-05-01T12:00:00Z"),
            ),
        )

    override suspend fun removeReaction(
        convoId: String,
        messageId: String,
        emoji: String,
    ): Result<MessageUi> =
        Result.success(
            MessageUi(
                id = messageId,
                senderDid = "did:plc:viewer",
                isOutgoing = true,
                text = "",
                isDeleted = false,
                sentAt = Instant.parse("2026-05-01T12:00:00Z"),
            ),
        )

    override suspend fun getLog(cursor: String?): Result<ChatLogPage> = Result.success(ChatLogPage())

    override suspend fun getConvoMembers(
        convoId: String,
        cursor: String?,
    ): Result<MemberPage> = Result.success(MemberPage())

    override suspend fun createGroup(
        name: String,
        dids: List<String>,
    ): Result<String> = Result.success("convo:new")

    override suspend fun addMembers(
        convoId: String,
        dids: List<String>,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun removeMembers(
        convoId: String,
        dids: List<String>,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun getJoinRequests(
        convoId: String,
        cursor: String?,
    ): Result<JoinRequestPage> = Result.success(JoinRequestPage())

    override suspend fun approveJoinRequest(
        convoId: String,
        did: String,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun rejectJoinRequest(
        convoId: String,
        did: String,
    ): Result<Unit> = Result.success(Unit)

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
