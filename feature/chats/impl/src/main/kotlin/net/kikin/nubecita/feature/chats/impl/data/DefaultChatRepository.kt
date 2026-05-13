package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoService
import io.github.kikin81.atproto.chat.bsky.convo.ListConvosRequest
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
                            ListConvosRequest(
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
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
                    // `cursor` is opaque appview state; the viewer's DID is PII. Log
                    // only the error identity per :core:auth's redaction policy.
                    Timber.tag(TAG).e(throwable, "listConvos failed: %s", throwable.javaClass.name)
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
