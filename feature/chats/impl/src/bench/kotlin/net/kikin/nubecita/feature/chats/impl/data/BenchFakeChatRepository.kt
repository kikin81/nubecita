package net.kikin.nubecita.feature.chats.impl.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.chats.impl.ChatHeader
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.MessageSendStatus
import net.kikin.nubecita.feature.chats.impl.MessageUi
import timber.log.Timber
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

@Singleton
internal class BenchFakeChatRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ChatRepository {
        private val initMutex = Mutex()
        private var isInitialized = false

        // Direct AND group fixtures; the sealed [ConvoRowUi] is patched via the
        // `withMuted` / `withUnread` / `withLastMessage` extensions (the sealed type
        // has no shared `copy`), matching the production cache patches.
        private val convosCache = ConcurrentHashMap<String, ConvoRowUi>()
        private val messagesCache = ConcurrentHashMap<String, List<MessageUi>>()
        private val didToConvoId = ConcurrentHashMap<String, String>()

        // Reactive published view of [convosCache], so the bench inbox updates
        // live on send just like production. null = not yet refreshed.
        private val convosFlow = MutableStateFlow<ImmutableList<ConvoRowUi>?>(null)

        // Bench has no message-request fixtures; the Requests segment renders its
        // empty state. Published as an empty (not null) list so it reads as loaded.
        private val requestConvosFlow =
            MutableStateFlow<ImmutableList<ConvoRowUi>?>(persistentListOf())

        private fun publishConvos() {
            convosFlow.value =
                convosCache.values
                    .sortedWith(compareByDescending<ConvoRowUi> { it.sentAt }.thenBy { it.convoId })
                    .toImmutableList()
        }

        private suspend fun ensureLoaded() =
            withContext(dispatcher) {
                if (isInitialized) return@withContext
                initMutex.withLock {
                    if (isInitialized) return@withLock
                    try {
                        val stream = context.assets.open(CHATS_ASSET_PATH)
                        val dto =
                            stream.use { input ->
                                @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
                                JSON.decodeFromStream(BenchConvoListDto.serializer(), input)
                            }
                        val viewerDid = currentViewerDid()
                        dto.convos.forEach { convoDto ->
                            val convoItem = BenchChatsMapper.toConvoListItem(convoDto)
                            convosCache[convoDto.convoId] = convoItem
                            // Only direct convos are reachable by peer DID (groups open
                            // by convoId from the list), so map by DID for direct only.
                            if (convoDto.kind == "direct" && convoDto.otherUserDid.isNotEmpty()) {
                                didToConvoId[convoDto.otherUserDid] = convoDto.convoId
                            }

                            val msgUis = convoDto.messages.map { BenchChatsMapper.toMessage(it, viewerDid) }
                            messagesCache[convoDto.convoId] = msgUis
                        }
                        // Only mark initialized after a successful load so a
                        // failed parse (e.g. fixture not yet packaged) retries
                        // on the next call rather than pinning an empty cache —
                        // mirrors BenchFakeFeedRepository's cache-on-success shape.
                        isInitialized = true
                    } catch (e: FileNotFoundException) {
                        Timber.tag(TAG).e(e, "chats.json asset not found")
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to load bench chats")
                    }
                }
            }

        override fun observeConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = convosFlow.asStateFlow()

        override fun observeRequestConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = requestConvosFlow.asStateFlow()

        override suspend fun refreshRequestConvos(): Result<Unit> = Result.success(Unit)

        override suspend fun leaveConvo(convoId: String): Result<Unit> {
            convosCache.remove(convoId)
            if (convosFlow.value != null) publishConvos()
            return Result.success(Unit)
        }

        // Bench seeds no message requests, so there's nothing to accept — success no-op.
        override suspend fun acceptConvo(convoId: String): Result<Unit> = Result.success(Unit)

        override suspend fun setMuted(
            convoId: String,
            muted: Boolean,
        ): Result<Unit> {
            convosCache[convoId]?.let { convosCache[convoId] = it.withMuted(muted) }
            if (convosFlow.value != null) publishConvos()
            return Result.success(Unit)
        }

        override suspend fun refreshConvos(): Result<Unit> {
            ensureLoaded()
            publishConvos()
            return Result.success(Unit)
        }

        override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
            ensureLoaded()
            val convoId = didToConvoId[otherUserDid]
            if (convoId != null) {
                // Only direct convos are reachable by peer DID; a group never maps here.
                val convo = convosCache[convoId] as? ConvoRowUi.Direct
                if (convo != null) {
                    return Result.success(
                        ConvoResolution(
                            convoId = convo.convoId,
                            otherUserHandle = convo.otherUserHandle,
                            otherUserDisplayName = convo.displayName,
                            otherUserAvatarUrl = convo.avatarUrl,
                        ),
                    )
                }
            }

            // Fallback for unmapped users
            val handle = "${otherUserDid.takeLast(6)}.bsky.social"
            val newConvoId = "convo_dyn_$otherUserDid"
            val resolution =
                ConvoResolution(
                    convoId = newConvoId,
                    otherUserHandle = handle,
                    otherUserDisplayName = "User ${otherUserDid.takeLast(4)}",
                    otherUserAvatarUrl = null,
                )

            val newConvo =
                ConvoRowUi.Direct(
                    convoId = newConvoId,
                    otherUserDid = otherUserDid,
                    otherUserHandle = handle,
                    displayName = resolution.otherUserDisplayName,
                    avatarUrl = null,
                    lastMessageSnippet = null,
                    lastMessageFromViewer = false,
                    lastMessageIsAttachment = false,
                    sentAt = null,
                )
            convosCache[newConvoId] = newConvo
            didToConvoId[otherUserDid] = newConvoId
            messagesCache[newConvoId] = emptyList()

            return Result.success(resolution)
        }

        override suspend fun getConvo(convoId: String): Result<ChatConvo> {
            ensureLoaded()
            // Unknown convoId → a real failure (surfaces the thread's InitialError),
            // not a blank dummy header. Mirrors production's error routing.
            val header =
                when (val convo = convosCache[convoId]) {
                    is ConvoRowUi.Group ->
                        ChatHeader.Group(name = convo.name, members = convo.members)
                    is ConvoRowUi.Direct ->
                        ChatHeader.Direct(
                            did = convo.otherUserDid,
                            handle = convo.otherUserHandle,
                            displayName = convo.displayName,
                            avatarUrl = convo.avatarUrl,
                        )
                    null ->
                        return Result.failure(NoSuchElementException("Unknown convoId: $convoId"))
                }
            return Result.success(
                ChatConvo(
                    convoId = convoId,
                    header = header,
                    canPost = true,
                ),
            )
        }

        // Bench fixtures embed every sender in the convo, so no out-of-roster
        // hydration is ever needed — return empty so the VM keeps its seeded profiles.
        override suspend fun getProfiles(dids: List<String>): Result<List<net.kikin.nubecita.data.models.AuthorUi>> = Result.success(emptyList())

        override suspend fun getMessages(
            convoId: String,
            cursor: String?,
            limit: Int,
        ): Result<MessagePage> {
            ensureLoaded()
            val msgs = messagesCache[convoId] ?: emptyList()
            return Result.success(
                MessagePage(
                    messages = msgs.toImmutableList(),
                    nextCursor = null,
                ),
            )
        }

        override suspend fun sendMessage(
            convoId: String,
            text: String,
        ): Result<MessageUi> {
            ensureLoaded()
            val viewerDid = currentViewerDid()
            val now = Clock.System.now()
            val newMsg =
                MessageUi(
                    id = "msg_${now.toEpochMilliseconds()}",
                    senderDid = viewerDid,
                    isOutgoing = true,
                    text = text,
                    isDeleted = false,
                    sentAt = now,
                    embed = null,
                    sendStatus = MessageSendStatus.Sent,
                )

            val currentMsgs = messagesCache[convoId] ?: emptyList()
            messagesCache[convoId] = listOf(newMsg) + currentMsgs

            val convo = convosCache[convoId]
            if (convo != null) {
                convosCache[convoId] =
                    convo.withLastMessage(
                        snippet = text,
                        fromViewer = true,
                        isAttachment = false,
                        sentAt = now,
                    )
                // Republish so an open inbox reflects the send live (only when the
                // list has been refreshed at least once — convosFlow is non-null).
                if (convosFlow.value != null) publishConvos()
            }

            return Result.success(newMsg)
        }

        override suspend fun markConvoRead(convoId: String): Result<Unit> {
            ensureLoaded()
            val convo = convosCache[convoId]
            if (convo != null && convo.unreadCount != 0) {
                convosCache[convoId] = convo.withUnread(0)
                if (convosFlow.value != null) publishConvos()
            }
            return Result.success(Unit)
        }

        // Bench has no real reactions backend; the toggle just no-ops visually. Echo
        // a minimal outgoing Sent message keyed by id so the VM's reconcile is benign.
        override suspend fun addReaction(
            convoId: String,
            messageId: String,
            emoji: String,
        ): Result<MessageUi> = Result.success(benchReactionEcho(messageId))

        override suspend fun removeReaction(
            convoId: String,
            messageId: String,
            emoji: String,
        ): Result<MessageUi> = Result.success(benchReactionEcho(messageId))

        private fun benchReactionEcho(messageId: String): MessageUi =
            MessageUi(
                id = messageId,
                senderDid = currentViewerDid(),
                isOutgoing = true,
                text = "",
                isDeleted = false,
                sentAt = Clock.System.now(),
            )

        // Bench has no firehose/log fixture and never registers the poll worker;
        // the inbox is fully served by the chats.json convo cache. Empty page.
        override suspend fun getLog(cursor: String?): Result<ChatLogPage> = Result.success(ChatLogPage())

        // Group-details roster lands in a later task; bench seeds no member fixtures yet.
        override suspend fun getConvoMembers(
            convoId: String,
            cursor: String?,
        ): Result<MemberPage> = Result.success(MemberPage())

        // Bench seeds no member fixtures yet; member management is a no-op success.
        override suspend fun addMembers(
            convoId: String,
            dids: List<String>,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun removeMembers(
            convoId: String,
            dids: List<String>,
        ): Result<Unit> = Result.success(Unit)

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private companion object {
            private const val TAG = "BenchFakeChatRepository"
            private const val CHATS_ASSET_PATH = "chats.json"

            private val JSON =
                Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                }
        }
    }
