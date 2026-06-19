package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.graph.Block
import io.github.kikin81.atproto.app.bsky.graph.GetBlocksRequest
import io.github.kikin81.atproto.app.bsky.graph.GraphService
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.DeleteRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.encodeRecord
import io.github.kikin81.atproto.runtime.parseOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.actors.BlockRepository
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.BlockedAccount
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
                    Timber.tag(TAG).w(throwable, "blockActor failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun blockedAccounts(): Result<List<BlockedAccount>> =
            withContext(dispatcher) {
                runCatching {
                    val service = GraphService(xrpcClientProvider.authenticated())
                    // getBlocks caps each page at 100; page through so a user with
                    // >100 blocks can see/manage all of them. Bounded by MAX_BLOCK_PAGES
                    // as a safety net against a pathological cursor loop.
                    val accounts = mutableListOf<BlockedAccount>()
                    var cursor: String? = null
                    var pages = 0
                    do {
                        val response = service.getBlocks(GetBlocksRequest(cursor = cursor, limit = GET_BLOCKS_LIMIT))
                        response.blocks.mapNotNullTo(accounts) { it.toBlockedAccount() }
                        cursor = response.cursor
                        pages++
                    } while (cursor != null && pages < MAX_BLOCK_PAGES)
                    accounts.toList()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.tag(TAG).w(throwable, "blockedAccounts failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun unblockActor(blockUri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val parts =
                        requireNotNull(AtUri(blockUri).parseOrNull()) { "block URI is not structurally valid" }
                    val rkey = requireNotNull(parts.rkey) { "block URI must be at://<repo>/<collection>/<rkey>" }
                    val client = xrpcClientProvider.authenticated()
                    RepoService(client).deleteRecord(
                        DeleteRecordRequest(collection = Nsid(BLOCK_NSID), repo = parts.repo, rkey = rkey),
                    )
                    Unit
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    // blockUri carries the viewer's DID — log only the throwable identity.
                    Timber.tag(TAG).w(throwable, "unblockActor failed: %s", throwable.javaClass.name)
                }
            }

        // Skip any block whose record URI is missing — without it there's nothing
        // to delete on unblock, so it can't be listed as actionable.
        private fun ProfileView.toBlockedAccount(): BlockedAccount? {
            val blockUri = viewer?.blocking?.raw ?: return null
            return BlockedAccount(
                did = did.raw,
                handle = handle.raw,
                displayName = displayName?.takeIf { it.isNotBlank() },
                avatarUrl = avatar?.raw,
                avatarHue = avatarHueFor(did = did.raw, handle = handle.raw),
                blockUri = blockUri,
            )
        }

        private fun currentViewerDid(): String = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did ?: throw NoSessionException()

        private companion object {
            const val TAG = "BlockRepository"
            const val BLOCK_NSID = "app.bsky.graph.block"
            const val GET_BLOCKS_LIMIT = 100L

            // Safety cap on pagination (100 × 20 = 2000 blocks); far beyond any
            // realistic block list, so it bounds a pathological cursor loop
            // without truncating real usage.
            const val MAX_BLOCK_PAGES = 20
        }
    }
