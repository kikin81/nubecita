package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.com.atproto.repo.GetRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.PutRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.XrpcError
import io.github.kikin81.atproto.runtime.present
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.chats.impl.AllowIncoming
import timber.log.Timber
import javax.inject.Inject

/**
 * Default [ChatSettingsRepository]: reads/writes the `chat.bsky.actor.declaration`
 * `self` record via `com.atproto.repo` `getRecord` / `putRecord`, mirroring the
 * record-merge + compare-and-swap pattern in `DefaultProfileRepository`.
 *
 * The record body is manipulated as a raw [JsonObject] (no typed SDK lexicon
 * dependency) so unmanaged / future keys survive a write byte-for-byte. A
 * missing record (`RecordNotFound`) reads as [AllowIncoming.Following] and
 * writes via the create path (no `swapRecord`, `$type` stamped).
 */
internal class DefaultChatSettingsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ChatSettingsRepository {
        override suspend fun getAllowIncoming(): Result<AllowIncoming> =
            withContext(dispatcher) {
                try {
                    val selfDid = currentViewerDid()
                    val repo = RepoService(xrpcClientProvider.authenticated())
                    val (record, _) = fetchDeclaration(repo, selfDid)
                    val wire = (record[KEY_ALLOW_INCOMING] as? JsonPrimitive)?.contentOrNull
                    Result.success(AllowIncoming.fromWire(wire))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "getAllowIncoming failed: %s", throwable.javaClass.name)
                    Result.failure(throwable)
                }
            }

        override suspend fun setAllowIncoming(value: AllowIncoming): Result<Unit> =
            withContext(dispatcher) {
                try {
                    val selfDid = currentViewerDid()
                    val repo = RepoService(xrpcClientProvider.authenticated())
                    // Fetch existing (+ CID for compare-and-swap), or fall into
                    // the create-if-missing path when the account has no
                    // declaration yet.
                    val (existing, swapCid) = fetchDeclaration(repo, selfDid)
                    val merged = existing.toMutableMap()
                    // Stamp $type only on create; an existing record already has it.
                    if (swapCid == null) {
                        merged[KEY_TYPE] = JsonPrimitive(DECLARATION_NSID)
                    }
                    merged[KEY_ALLOW_INCOMING] = JsonPrimitive(value.wireValue)
                    repo.putRecord(
                        PutRecordRequest(
                            collection = Nsid(DECLARATION_NSID),
                            repo = AtIdentifier(selfDid),
                            rkey = RecordKey(DECLARATION_RKEY),
                            record = JsonObject(merged),
                            // Present only when we read a prior CID; the create
                            // path omits it so the first write can't fail on a
                            // missing-record compare-and-swap.
                            swapRecord = swapCid?.let { present(it) } ?: AtField.Missing,
                        ),
                    )
                    Result.success(Unit)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "setAllowIncoming failed: %s", throwable.javaClass.name)
                    Result.failure(throwable)
                }
            }

        /**
         * Fetches the `chat.bsky.actor.declaration`/`self` record paired with its
         * CID. A `RecordNotFound` XRPC error (no declaration yet) returns an empty
         * body with a `null` CID — the create-if-missing signal.
         */
        private suspend fun fetchDeclaration(
            repo: RepoService,
            selfDid: String,
        ): Pair<JsonObject, Cid?> =
            try {
                val response =
                    repo.getRecord(
                        GetRecordRequest(
                            collection = Nsid(DECLARATION_NSID),
                            repo = AtIdentifier(selfDid),
                            rkey = RecordKey(DECLARATION_RKEY),
                        ),
                    )
                (response.value as? JsonObject ?: JsonObject(emptyMap())) to response.cid
            } catch (error: XrpcError) {
                if (error.errorName == ERROR_RECORD_NOT_FOUND) {
                    JsonObject(emptyMap()) to null
                } else {
                    throw error
                }
            }

        private fun currentViewerDid(): String =
            (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                ?: throw NoSessionException()

        private companion object {
            const val TAG = "ChatSettingsRepo"
            const val DECLARATION_NSID = "chat.bsky.actor.declaration"
            const val DECLARATION_RKEY = "self"
            const val KEY_TYPE = "\$type"
            const val KEY_ALLOW_INCOMING = "allowIncoming"
            const val ERROR_RECORD_NOT_FOUND = "RecordNotFound"
        }
    }
