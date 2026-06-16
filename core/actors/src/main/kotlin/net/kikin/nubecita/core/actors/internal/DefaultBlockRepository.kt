package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.app.bsky.graph.Block
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.encodeRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Clock

/**
 * Production [BlockRepository] — writes an `app.bsky.graph.block` record via the
 * SDK's `com.atproto.repo.createRecord`, in the viewer's own repo. Mirrors the
 * record-creation shape of `DefaultLikeRepostRepository` (encode typed record →
 * createRecord); the block's `subject` is the target account's DID.
 */
@Singleton
internal class DefaultBlockRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : BlockRepository {
        override suspend fun blockActor(did: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val record =
                        encodeRecord(
                            record = Block(createdAt = Datetime(Clock.System.now().toString()), subject = Did(did)),
                            type = BLOCK_NSID,
                        )
                    RepoService(client).createRecord(
                        CreateRecordRequest(
                            collection = Nsid(BLOCK_NSID),
                            repo = AtIdentifier(viewerDid),
                            record = record,
                        ),
                    )
                    Unit
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).e(throwable, "blockActor failed: %s", throwable.javaClass.name)
                }
            }

        private fun currentViewerDid(): String = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did ?: throw NoSessionException()

        private companion object {
            const val TAG = "BlockRepository"
            const val BLOCK_NSID = "app.bsky.graph.block"
        }
    }
