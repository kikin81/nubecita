package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetProfilesRequest
import io.github.kikin81.atproto.chat.bsky.convo.AcceptConvoRequest
import io.github.kikin81.atproto.chat.bsky.convo.AddReactionRequest
import io.github.kikin81.atproto.chat.bsky.convo.ConvoService
import io.github.kikin81.atproto.chat.bsky.convo.GetConvoForMembersRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetConvoMembersRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetConvoRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetLogRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesRequest
import io.github.kikin81.atproto.chat.bsky.convo.LeaveConvoRequest
import io.github.kikin81.atproto.chat.bsky.convo.ListConvosRequest
import io.github.kikin81.atproto.chat.bsky.convo.MessageInput
import io.github.kikin81.atproto.chat.bsky.convo.MuteConvoRequest
import io.github.kikin81.atproto.chat.bsky.convo.RemoveReactionRequest
import io.github.kikin81.atproto.chat.bsky.convo.SendMessageRequest
import io.github.kikin81.atproto.chat.bsky.convo.UnmuteConvoRequest
import io.github.kikin81.atproto.chat.bsky.convo.UpdateReadRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.Did
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
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
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
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
        // Single source of truth for the ACCEPTED convo list, shared across both
        // screens (this repository is @Singleton-bound). null = not loaded yet.
        private val convosCache = MutableStateFlow<ImmutableList<ConvoRowUi>?>(null)

        // Pending message REQUESTS (status=request). Separate cache so requests
        // never inflate the unread badge and a request-fetch failure can't poison
        // the accepted list. null = not loaded yet.
        private val requestConvosCache = MutableStateFlow<ImmutableList<ConvoRowUi>?>(null)

        override fun observeConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = convosCache.asStateFlow()

        override fun observeRequestConvos(): StateFlow<ImmutableList<ConvoRowUi>?> = requestConvosCache.asStateFlow()

        override suspend fun refreshConvos(): Result<Unit> = refreshConvosWithStatus(STATUS_ACCEPTED, convosCache)

        override suspend fun refreshRequestConvos(): Result<Unit> = refreshConvosWithStatus(STATUS_REQUEST, requestConvosCache)

        private suspend fun refreshConvosWithStatus(
            status: String,
            cache: MutableStateFlow<ImmutableList<ConvoRowUi>?>,
        ): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).listConvos(
                            ListConvosRequest(cursor = null, limit = LIST_CONVOS_PAGE_LIMIT.toLong(), status = status),
                        )
                    cache.value =
                        response.convos
                            .map { it.toConvoRowUi(viewerDid = viewerDid) }
                            .toImmutableList()
                }.onFailure { throwable ->
                    // Never swallow cancellation — let it propagate so the coroutine
                    // tears down cleanly and we don't log a cancel as a network failure.
                    if (throwable is CancellationException) throw throwable
                    // Leave the cache untouched so a failed refresh keeps the prior list.
                    Timber.tag(TAG).w(throwable, "refreshConvos(%s) failed: %s", status, throwable.javaClass.name)
                }
            }

        override suspend fun leaveConvo(convoId: String): Result<Unit> =
            convoMutation("leaveConvo") { service ->
                service.leaveConvo(LeaveConvoRequest(convoId = convoId))
                // A convo may be accepted or a request — drop it from both caches.
                convosCache.update { patchConvosOnLeave(it, convoId) }
                requestConvosCache.update { patchConvosOnLeave(it, convoId) }
            }

        override suspend fun acceptConvo(convoId: String): Result<Unit> =
            convoMutation("acceptConvo") { service ->
                service.acceptConvo(AcceptConvoRequest(convoId = convoId))
                // Move it out of requests into the front of accepted as two
                // independent atomic `update`s — capture the row while removing
                // it from requests, then prepend it to accepted. A snapshot-both-
                // then-assign-both would clobber a concurrent patch to either
                // cache (send / mark-read / poll) with a stale write.
                var moved: ConvoRowUi? = null
                requestConvosCache.update { current ->
                    moved = current?.firstOrNull { it.convoId == convoId }
                    patchConvosOnLeave(current, convoId)
                }
                moved?.let { convo -> convosCache.update { patchConvosPrepend(it, convo) } }
            }

        override suspend fun setMuted(
            convoId: String,
            muted: Boolean,
        ): Result<Unit> =
            convoMutation("setMuted") { service ->
                if (muted) {
                    service.muteConvo(MuteConvoRequest(convoId = convoId))
                } else {
                    service.unmuteConvo(UnmuteConvoRequest(convoId = convoId))
                }
                convosCache.update { patchConvosOnMute(it, convoId, muted) }
                requestConvosCache.update { patchConvosOnMute(it, convoId, muted) }
            }

        // Shared shape for the one-shot convo mutations (leave/accept/mute): run the
        // XRPC call + cache patch on the IO dispatcher; rethrow cancellation; log and
        // return failure otherwise (the cache is only patched after the call succeeds,
        // so a failure leaves the cached list untouched).
        private suspend inline fun convoMutation(
            op: String,
            crossinline block: suspend (ConvoService) -> Unit,
        ): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    block(ConvoService(xrpcClientProvider.authenticated()))
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "%s failed: %s", op, throwable.javaClass.name)
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
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "resolveConvo failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun getConvo(convoId: String): Result<ChatConvo> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val convo = ConvoService(client).getConvo(GetConvoRequest(convoId = convoId)).convo
                    ChatConvo(
                        convoId = convo.id,
                        header = convo.toChatHeader(viewerDid),
                        canPost = convo.canViewerPost(viewerDid),
                    )
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "getConvo failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun getProfiles(dids: List<String>): Result<List<AuthorUi>> =
            withContext(dispatcher) {
                runCatching {
                    if (dids.isEmpty()) return@runCatching emptyList()
                    val service = ActorService(xrpcClientProvider.authenticated())
                    // app.bsky.actor.getProfiles caps `actors` at 25 per call. Hydration
                    // is best-effort: catch per chunk so one failing batch still yields the
                    // others' profiles (partial degradation beats all-or-nothing).
                    dids.distinct().chunked(GET_PROFILES_MAX_ACTORS).flatMap { chunk ->
                        try {
                            service
                                .getProfiles(GetProfilesRequest(actors = chunk.map { AtIdentifier(it) }))
                                .profiles
                                .map { it.toAuthorUi() }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // `dids` are PII — log only the failure identity, not the values.
                            Timber.tag(TAG).w(e, "getProfiles chunk failed: %s", e.javaClass.name)
                            emptyList()
                        }
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    // `dids` are PII — log only the failure identity, not the values.
                    Timber.tag(TAG).w(throwable, "getProfiles failed: %s", throwable.javaClass.name)
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
                    Timber.tag(TAG).w(throwable, "getMessages failed: %s", throwable.javaClass.name)
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
                    Timber.tag(TAG).w(throwable, "sendMessage failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun addReaction(
            convoId: String,
            messageId: String,
            emoji: String,
        ): Result<MessageUi> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    ConvoService(client)
                        .addReaction(AddReactionRequest(convoId = convoId, messageId = messageId, value = emoji))
                        .message
                        .toMessageUi(viewerDid)
                }.onFailure {
                    if (it is CancellationException) throw it
                    Timber.tag(TAG).w(it, "addReaction failed: %s", it.javaClass.name)
                }
            }

        override suspend fun removeReaction(
            convoId: String,
            messageId: String,
            emoji: String,
        ): Result<MessageUi> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    ConvoService(client)
                        .removeReaction(RemoveReactionRequest(convoId = convoId, messageId = messageId, value = emoji))
                        .message
                        .toMessageUi(viewerDid)
                }.onFailure {
                    if (it is CancellationException) throw it
                    Timber.tag(TAG).w(it, "removeReaction failed: %s", it.javaClass.name)
                }
            }

        override suspend fun getLog(cursor: String?): Result<ChatLogPage> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    ConvoService(client)
                        .getLog(GetLogRequest(cursor = cursor))
                        .toChatLogPage()
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "getLog failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun getConvoMembers(
            convoId: String,
            cursor: String?,
        ): Result<MemberPage> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).getConvoMembers(
                            GetConvoMembersRequest(
                                convoId = convoId,
                                limit = GET_CONVO_MEMBERS_PAGE_LIMIT.toLong(),
                                cursor = cursor,
                            ),
                        )
                    MemberPage(
                        members = response.members.map { it.toGroupMemberUi(viewerDid) }.toImmutableList(),
                        cursor = response.cursor,
                    )
                }.onFailure {
                    if (it is CancellationException) throw it
                    Timber.tag(TAG).w(it, "getConvoMembers failed: %s", it.javaClass.name)
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
                    Timber.tag(TAG).w(throwable, "markConvoRead failed: %s", throwable.javaClass.name)
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

            // chat.bsky.convo.listConvos `status` filter values.
            const val STATUS_ACCEPTED = "accepted"
            const val STATUS_REQUEST = "request"

            // app.bsky.actor.getProfiles caps `actors` at 25 per request.
            const val GET_PROFILES_MAX_ACTORS = 25
        }
    }
