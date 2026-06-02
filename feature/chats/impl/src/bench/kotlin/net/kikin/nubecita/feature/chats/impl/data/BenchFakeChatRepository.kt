package net.kikin.nubecita.feature.chats.impl.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.profile.avatarHueFor
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
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

        private val convosCache = ConcurrentHashMap<String, ConvoListItemUi>()
        private val messagesCache = ConcurrentHashMap<String, List<MessageUi>>()
        private val didToConvoId = ConcurrentHashMap<String, String>()

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
                            didToConvoId[convoDto.otherUserDid] = convoDto.convoId

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

        override suspend fun listConvos(
            cursor: String?,
            limit: Int,
        ): Result<ConvoListPage> {
            ensureLoaded()
            val sortedConvos =
                convosCache.values
                    .sortedWith(
                        compareByDescending<ConvoListItemUi> { it.sentAt }
                            .thenBy { it.convoId },
                    ).toImmutableList()
            return Result.success(
                ConvoListPage(
                    items = sortedConvos,
                    nextCursor = null,
                ),
            )
        }

        override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
            ensureLoaded()
            val convoId = didToConvoId[otherUserDid]
            if (convoId != null) {
                val convo = convosCache[convoId]
                if (convo != null) {
                    return Result.success(
                        ConvoResolution(
                            convoId = convo.convoId,
                            otherUserHandle = convo.otherUserHandle,
                            otherUserDisplayName = convo.displayName,
                            otherUserAvatarUrl = convo.avatarUrl,
                            otherUserAvatarHue = convo.avatarHue,
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
                    otherUserAvatarHue = avatarHueFor(otherUserDid, handle),
                )

            val newConvo =
                ConvoListItemUi(
                    convoId = newConvoId,
                    otherUserDid = otherUserDid,
                    otherUserHandle = handle,
                    displayName = resolution.otherUserDisplayName,
                    avatarUrl = null,
                    avatarHue = resolution.otherUserAvatarHue,
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
                    convo.copy(
                        lastMessageSnippet = text,
                        lastMessageFromViewer = true,
                        lastMessageIsAttachment = false,
                        sentAt = now,
                    )
            }

            return Result.success(newMsg)
        }

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
