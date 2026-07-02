package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinResult
import net.kikin.nubecita.feature.chats.impl.JoinRule
import net.kikin.nubecita.feature.chats.impl.data.ChatConvo
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.JoinRequestPage
import net.kikin.nubecita.feature.chats.impl.data.MemberPage
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnAccept
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnLeave
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnMute
import net.kikin.nubecita.feature.chats.impl.data.patchConvosOnRead
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

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
    var nextRequestRefreshResult: Result<ImmutableList<ConvoRowUi>> = Result.success(persistentListOf()),
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
    var lastSendReplyToMessageId: String? = null
    var lastMarkReadConvoId: String? = null
    var nextMarkReadResult: Result<Unit> = Result.success(Unit)
    val getLogCalls = AtomicInteger(0)
    var lastGetLogCursor: String? = null
    var nextGetLogResult: Result<ChatLogPage> = Result.success(ChatLogPage())
    val refreshRequestCalls = AtomicInteger(0)
    val getConvoCalls = AtomicInteger(0)
    var lastGetConvoId: String? = null
    var getConvoResult: Result<ChatConvo> =
        Result.success(
            ChatConvo(
                convoId = "convo-1",
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

    private val convos = MutableStateFlow<ImmutableList<ConvoRowUi>?>(null)
    private val requestConvos = MutableStateFlow<ImmutableList<ConvoRowUi>?>(null)

    override fun observeConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = convos.asStateFlow()

    override fun observeRequestConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = requestConvos.asStateFlow()

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
    fun emitConvos(items: ImmutableList<ConvoRowUi>?) {
        convos.value = items
    }

    /** Drive the request cache directly (simulating an external update). */
    fun emitRequestConvos(items: ImmutableList<ConvoRowUi>?) {
        requestConvos.value = items
    }

    override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
        resolveCalls.incrementAndGet()
        lastResolvedDid = otherUserDid
        return nextResolveResult
    }

    override suspend fun getConvo(convoId: String): Result<ChatConvo> {
        getConvoCalls.incrementAndGet()
        lastGetConvoId = convoId
        return getConvoResult
    }

    val getProfilesCalls = mutableListOf<List<String>>()
    var getProfilesResult: Result<List<AuthorUi>> = Result.success(emptyList())

    override suspend fun getProfiles(dids: List<String>): Result<List<AuthorUi>> {
        getProfilesCalls += dids
        return getProfilesResult
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
        replyToMessageId: String?,
    ): Result<MessageUi> {
        sendCalls.incrementAndGet()
        lastSendConvoId = convoId
        lastSendText = text
        lastSendReplyToMessageId = replyToMessageId
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

    var getConvoMembersResult: Result<MemberPage> = Result.success(MemberPage())
    var lastGetConvoMembersConvoId: String? = null
    var lastGetConvoMembersCursor: String? = null
    val getConvoMembersCalls = AtomicInteger(0)

    override suspend fun getConvoMembers(
        convoId: String,
        cursor: String?,
    ): Result<MemberPage> {
        getConvoMembersCalls.incrementAndGet()
        lastGetConvoMembersConvoId = convoId
        lastGetConvoMembersCursor = cursor
        return getConvoMembersResult
    }

    var createGroupResult: Result<String> = Result.success("convo:new")
    val createGroupCalls = mutableListOf<Pair<String, List<String>>>()

    /**
     * Optional gate: when set, `createGroup` records its call and then suspends on
     * this deferred before returning. Lets a test hold a create in flight so the
     * VM's `isSubmitting` input-lock can be observed (picker edits and a second
     * create are dropped while the first is parked here).
     */
    var createGroupGate: CompletableDeferred<Unit>? = null

    override suspend fun createGroup(
        name: String,
        dids: List<String>,
    ): Result<String> {
        createGroupCalls += name to dids
        createGroupGate?.await()
        return createGroupResult
    }

    var addMembersResult: Result<Unit> = Result.success(Unit)
    var removeMembersResult: Result<Unit> = Result.success(Unit)
    val addMembersCalls = mutableListOf<Pair<String, List<String>>>()
    val removeMembersCalls = mutableListOf<Pair<String, List<String>>>()

    /**
     * Optional gate: when set, `removeMembers` records its call and then suspends
     * on this deferred before returning. Lets a test hold a removal in flight so
     * the VM's `inFlightRemovals` guard can be observed (a second remove of the
     * same DID is dropped while the first is parked here).
     */
    var removeMembersGate: CompletableDeferred<Unit>? = null

    /**
     * Optional per-call results for `removeMembers`, consumed in order. When
     * non-empty, each call dequeues the next result (falling back to
     * [removeMembersResult] once exhausted) — lets a test fail one interleaved
     * removal while another succeeds.
     */
    val removeMembersResults = ArrayDeque<Result<Unit>>()

    override suspend fun addMembers(
        convoId: String,
        dids: List<String>,
    ): Result<Unit> {
        addMembersCalls += convoId to dids
        return addMembersResult
    }

    override suspend fun removeMembers(
        convoId: String,
        dids: List<String>,
    ): Result<Unit> {
        removeMembersCalls += convoId to dids
        removeMembersGate?.await()
        return removeMembersResults.removeFirstOrNull() ?: removeMembersResult
    }

    var getJoinRequestsResult: Result<JoinRequestPage> = Result.success(JoinRequestPage())
    var approveJoinRequestResult: Result<Unit> = Result.success(Unit)
    var rejectJoinRequestResult: Result<Unit> = Result.success(Unit)
    val approveJoinRequestCalls = mutableListOf<Pair<String, String>>()
    val rejectJoinRequestCalls = mutableListOf<Pair<String, String>>()

    /** Optional gate: when set, `approveJoinRequest` suspends on it before returning (test in-flight states). */
    var approveJoinRequestGate: CompletableDeferred<Unit>? = null

    override suspend fun getJoinRequests(
        convoId: String,
        cursor: String?,
    ): Result<JoinRequestPage> = getJoinRequestsResult

    override suspend fun approveJoinRequest(
        convoId: String,
        did: String,
    ): Result<Unit> {
        approveJoinRequestCalls += convoId to did
        approveJoinRequestGate?.await()
        return approveJoinRequestResult
    }

    override suspend fun rejectJoinRequest(
        convoId: String,
        did: String,
    ): Result<Unit> {
        rejectJoinRequestCalls += convoId to did
        return rejectJoinRequestResult
    }

    var getJoinLinkResult: Result<JoinLinkUi?> = Result.success(null)
    var createJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK)
    var editJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK)
    var enableJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK.copy(enabled = true))
    var disableJoinLinkResult: Result<JoinLinkUi> = Result.success(DEFAULT_JOIN_LINK.copy(enabled = false))
    val editJoinLinkCalls = mutableListOf<Triple<String, JoinRule?, Boolean?>>()
    val enableJoinLinkCalls = mutableListOf<String>()
    val disableJoinLinkCalls = mutableListOf<String>()
    val createJoinLinkCalls = mutableListOf<Triple<String, JoinRule, Boolean>>()
    val getJoinLinkCalls = mutableListOf<String>()

    /** Optional gate: when set, the four link mutations suspend on it before returning. */
    var joinLinkMutationGate: CompletableDeferred<Unit>? = null

    override suspend fun getJoinLink(convoId: String): Result<JoinLinkUi?> {
        getJoinLinkCalls += convoId
        return getJoinLinkResult
    }

    override suspend fun createJoinLink(
        convoId: String,
        joinRule: JoinRule,
        requireApproval: Boolean,
    ): Result<JoinLinkUi> {
        createJoinLinkCalls += Triple(convoId, joinRule, requireApproval)
        joinLinkMutationGate?.await()
        return createJoinLinkResult
    }

    override suspend fun editJoinLink(
        convoId: String,
        joinRule: JoinRule?,
        requireApproval: Boolean?,
    ): Result<JoinLinkUi> {
        editJoinLinkCalls += Triple(convoId, joinRule, requireApproval)
        joinLinkMutationGate?.await()
        return editJoinLinkResult
    }

    override suspend fun enableJoinLink(convoId: String): Result<JoinLinkUi> {
        enableJoinLinkCalls += convoId
        joinLinkMutationGate?.await()
        return enableJoinLinkResult
    }

    override suspend fun disableJoinLink(convoId: String): Result<JoinLinkUi> {
        disableJoinLinkCalls += convoId
        joinLinkMutationGate?.await()
        return disableJoinLinkResult
    }

    var getGroupPublicInfoResult: Result<GroupPublicInfoUi> = Result.success(DEFAULT_GROUP_PUBLIC_INFO)
    var requestJoinResult: Result<JoinResult> = Result.success(JoinResult.Pending)
    val getGroupPublicInfoCalls = mutableListOf<String>()
    val requestJoinCalls = mutableListOf<String>()

    /** Optional gate: when set, `requestJoin` suspends on it before returning (test in-flight states). */
    var requestJoinGate: CompletableDeferred<Unit>? = null

    override suspend fun getGroupPublicInfo(code: String): Result<GroupPublicInfoUi> {
        getGroupPublicInfoCalls += code
        return getGroupPublicInfoResult
    }

    override suspend fun requestJoin(code: String): Result<JoinResult> {
        requestJoinCalls += code
        requestJoinGate?.await()
        return requestJoinResult
    }

    val addReactionCalls = mutableListOf<Triple<String, String, String>>() // convoId, messageId, emoji
    val removeReactionCalls = mutableListOf<Triple<String, String, String>>()
    var addReactionResult: Result<MessageUi>? = null // null → echo DEFAULT_SENT_MESSAGE.copy(id = messageId)
    var removeReactionResult: Result<MessageUi>? = null

    /** Optional gate: when set, add/removeReaction suspends on it before returning (test in-flight states). */
    var reactionGate: CompletableDeferred<Unit>? = null

    override suspend fun addReaction(
        convoId: String,
        messageId: String,
        emoji: String,
    ): Result<MessageUi> {
        addReactionCalls += Triple(convoId, messageId, emoji)
        reactionGate?.await()
        return addReactionResult ?: Result.success(DEFAULT_SENT_MESSAGE.copy(id = messageId))
    }

    override suspend fun removeReaction(
        convoId: String,
        messageId: String,
        emoji: String,
    ): Result<MessageUi> {
        removeReactionCalls += Triple(convoId, messageId, emoji)
        reactionGate?.await()
        return removeReactionResult ?: Result.success(DEFAULT_SENT_MESSAGE.copy(id = messageId))
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

        val DEFAULT_JOIN_LINK =
            JoinLinkUi(
                code = "code-1",
                url = "https://nubecita.app/group/join/code-1",
                enabled = true,
                joinRule = JoinRule.Anyone,
                requireApproval = true,
                createdAt = Instant.parse("2026-05-13T12:00:00Z"),
            )

        val DEFAULT_GROUP_PUBLIC_INFO =
            GroupPublicInfoUi(
                name = "Group",
                memberCount = 3,
                ownerDisplayName = "Owner",
                ownerHandle = "owner.bsky.social",
                ownerAvatarUrl = null,
                requireApproval = true,
            )
    }
}
