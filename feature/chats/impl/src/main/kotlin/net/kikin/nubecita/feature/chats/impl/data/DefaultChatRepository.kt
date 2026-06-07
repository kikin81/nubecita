package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoService
import io.github.kikin81.atproto.chat.bsky.convo.GetConvoForMembersRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesRequest
import io.github.kikin81.atproto.chat.bsky.convo.ListConvosRequest
import io.github.kikin81.atproto.chat.bsky.convo.MessageInput
import io.github.kikin81.atproto.chat.bsky.convo.SendMessageRequest
import io.github.kikin81.atproto.chat.bsky.convo.UpdateReadRequest
import io.github.kikin81.atproto.runtime.Did
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.profile.avatarHueFor
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi
import timber.log.Timber
import javax.inject.Inject

internal class DefaultChatRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ChatRepository {
        // Single source of truth for the convo list, shared across both screens
        // (this repository is @Singleton-bound). null = not loaded yet.
        private val convosCache = MutableStateFlow<ImmutableList<ConvoListItemUi>?>(null)

        override fun observeConvos(): StateFlow<ImmutableList<ConvoListItemUi>?> = convosCache.asStateFlow()

        override suspend fun refreshConvos(): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).listConvos(
                            ListConvosRequest(cursor = null, limit = LIST_CONVOS_PAGE_LIMIT.toLong()),
                        )
                    convosCache.value =
                        response.convos
                            .map { it.toConvoListItemUi(viewerDid = viewerDid) }
                            .toImmutableList()
                }.onFailure { throwable ->
                    // Leave the cache untouched so a failed refresh keeps the prior list.
                    Timber.tag(TAG).e(throwable, "refreshConvos failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).getConvoForMembers(
                            GetConvoForMembersRequest(members = listOf(Did(otherUserDid))),
                        )
                    val convo = response.convo
                    val other =
                        convo.members.firstOrNull { it.did.raw != viewerDid }
                            ?: convo.members.firstOrNull()
                            ?: error("getConvoForMembers returned no members — protocol violation")
                    ConvoResolution(
                        convoId = convo.id,
                        otherUserHandle = other.handle.raw,
                        otherUserDisplayName = other.displayName?.takeUnless { it.isBlank() },
                        otherUserAvatarUrl = other.avatar?.raw,
                        otherUserAvatarHue = avatarHueFor(did = other.did.raw, handle = other.handle.raw),
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "resolveConvo failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun getMessages(
            convoId: String,
            cursor: String?,
            limit: Int,
        ): Result<MessagePage> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).getMessages(
                            GetMessagesRequest(
                                convoId = convoId,
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    MessagePage(
                        messages = response.messages.toMessageUis(viewerDid = viewerDid),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "getMessages failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun sendMessage(
            convoId: String,
            text: String,
        ): Result<MessageUi> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).sendMessage(
                            SendMessageRequest(
                                convoId = convoId,
                                message = MessageInput(text = text),
                            ),
                        )
                    response.toMessageUi(viewerDid = viewerDid).also { patchConvoOnSend(convoId, it) }
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "sendMessage failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun markConvoRead(convoId: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    ConvoService(client).updateRead(UpdateReadRequest(convoId = convoId))
                    // Optimistically zero the cached convo so the in-row + bottom-nav
                    // badges flip to read without waiting for the next refreshConvos.
                    convosCache.update { current -> patchConvosOnRead(current, convoId) }
                    Unit
                }.onFailure { throwable ->
                    // Best-effort: leave the cache untouched; the badge corrects on
                    // the next refresh. Not surfaced to the user.
                    Timber.tag(TAG).e(throwable, "markConvoRead failed: %s", throwable.javaClass.name)
                }
            }

        private fun patchConvoOnSend(
            convoId: String,
            message: MessageUi,
        ) {
            convosCache.update { current -> patchConvosOnSend(current, convoId, message) }
        }

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private companion object {
            const val TAG = "ChatRepository"
        }
    }
