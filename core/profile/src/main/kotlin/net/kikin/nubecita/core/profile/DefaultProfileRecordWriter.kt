package net.kikin.nubecita.core.profile

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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import timber.log.Timber
import javax.inject.Inject

/**
 * Production [ProfileRecordWriter]. Public (not `internal`) so feature-module
 * tests can construct it against a mock XRPC engine.
 */
class DefaultProfileRecordWriter
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProfileRecordWriter {
        override suspend fun update(transform: suspend (existing: JsonObject?) -> JsonObject?): Result<Boolean> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    val selfDid =
                        (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                            ?: throw NoSessionException()
                    val repo = RepoService(xrpcClientProvider.authenticated())
                    val (existing, swapCid) = fetch(repo, selfDid)
                    val next = transform(existing) ?: return@runCatchingCancellable false
                    val record =
                        if (existing == null && KEY_TYPE !in next) {
                            JsonObject(next + (KEY_TYPE to JsonPrimitive(PROFILE_NSID)))
                        } else {
                            next
                        }
                    repo.putRecord(
                        PutRecordRequest(
                            collection = Nsid(PROFILE_NSID),
                            repo = AtIdentifier(selfDid),
                            rkey = RecordKey(PROFILE_RKEY),
                            record = record,
                            // Omitted on the create path so a first write can't
                            // fail on a missing-record compare-and-swap.
                            swapRecord = swapCid?.let { present(it) } ?: AtField.Missing,
                        ),
                    )
                    true
                }.onFailure { throwable ->
                    // The record body and DID stay out of logs; error identity only.
                    Timber.tag(TAG).w(throwable, "profile record update failed: %s", throwable.javaClass.name)
                }
            }

        private suspend fun fetch(
            repo: RepoService,
            selfDid: String,
        ): Pair<JsonObject?, Cid?> =
            try {
                val response =
                    repo.getRecord(
                        GetRecordRequest(
                            collection = Nsid(PROFILE_NSID),
                            repo = AtIdentifier(selfDid),
                            rkey = RecordKey(PROFILE_RKEY),
                        ),
                    )
                response.value to response.cid
            } catch (error: XrpcError) {
                if (error.errorName == ERROR_RECORD_NOT_FOUND) null to null else throw error
            }

        private companion object {
            const val TAG = "ProfileRecordWriter"
            const val PROFILE_NSID = "app.bsky.actor.profile"
            const val PROFILE_RKEY = "self"
            const val KEY_TYPE = "\$type"
            const val ERROR_RECORD_NOT_FOUND = "RecordNotFound"
        }
    }
