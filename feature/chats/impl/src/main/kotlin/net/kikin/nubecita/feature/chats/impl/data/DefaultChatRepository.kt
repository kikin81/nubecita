package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoService
import io.github.kikin81.atproto.chat.bsky.convo.GetConvoForMembersRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesRequest
import io.github.kikin81.atproto.chat.bsky.convo.ListConvosRequest
import io.github.kikin81.atproto.runtime.Did
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

internal class DefaultChatRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ChatRepository {
        override suspend fun listConvos(
            cursor: String?,
            limit: Int,
        ): Result<ConvoListPage> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).listConvos(
                            ListConvosRequest(cursor = cursor, limit = limit.toLong()),
                        )
                    val now = Clock.System.now()
                    ConvoListPage(
                        items =
                            response.convos
                                .map { it.toConvoListItemUi(viewerDid = viewerDid, now = now) }
                                .toImmutableList(),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "listConvos failed: %s", throwable.javaClass.name)
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
