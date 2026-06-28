package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.app.bsky.graph.GraphService
import io.github.kikin81.atproto.app.bsky.graph.MuteActorRequest
import io.github.kikin81.atproto.app.bsky.graph.UnmuteActorRequest
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [MuteRepository] — calls `app.bsky.graph.muteActor` and
 * `app.bsky.graph.unmuteActor` via the SDK's [GraphService]. Unlike
 * blocking, muting is a simple XRPC procedure with no record creation.
 */
@Singleton
internal class DefaultMuteRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : MuteRepository {
        override suspend fun muteActor(did: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    GraphService(client).muteActor(MuteActorRequest(actor = AtIdentifier(did)))
                    Unit
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "muteActor failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun unmuteActor(did: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val client = xrpcClientProvider.authenticated()
                    GraphService(client).unmuteActor(UnmuteActorRequest(actor = AtIdentifier(did)))
                    Unit
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "unmuteActor failed: %s", throwable.javaClass.name)
                }
            }

        private companion object {
            const val TAG = "MuteRepository"
        }
    }
