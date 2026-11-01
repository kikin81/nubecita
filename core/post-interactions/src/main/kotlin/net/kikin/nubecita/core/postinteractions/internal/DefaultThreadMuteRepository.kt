package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.app.bsky.graph.GraphService
import io.github.kikin81.atproto.app.bsky.graph.MuteThreadRequest
import io.github.kikin81.atproto.app.bsky.graph.UnmuteThreadRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.postinteractions.ThreadMuteRepository
import timber.log.Timber
import javax.inject.Inject

internal class DefaultThreadMuteRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ThreadMuteRepository {
        override suspend fun mute(rootUri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    GraphService(xrpcClientProvider.authenticated()).muteThread(MuteThreadRequest(root = AtUri(rootUri)))
                    Unit
                }.onFailure { throwable ->
                    // The root URI carries an author DID — log the error identity only.
                    Timber.tag(TAG).w(throwable, "muteThread failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun unmute(rootUri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    GraphService(xrpcClientProvider.authenticated()).unmuteThread(UnmuteThreadRequest(root = AtUri(rootUri)))
                    Unit
                }.onFailure { throwable ->
                    Timber.tag(TAG).w(throwable, "unmuteThread failed: %s", throwable.javaClass.name)
                }
            }

        private companion object {
            const val TAG = "ThreadMuteRepository"
        }
    }
