package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.app.bsky.graph.Follow
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.DeleteRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.encodeRecord
import io.github.kikin81.atproto.runtime.parseOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.postinteractions.FollowRepository
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Default [FollowRepository] mirroring [DefaultLikeRepostRepository] — same
 * `RepoService.createRecord` / `deleteRecord` shape, same `currentViewerDid()`
 * helper, and the same redaction discipline (the subject DID and the
 * viewer-DID-bearing follow URI are kept out of log surfaces).
 *
 * Extracted from `:feature:profile:impl/DefaultProfileRepository` so both
 * profile and group-details (chats) write follows through one path.
 */
internal class DefaultFollowRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : FollowRepository {
        override suspend fun follow(subjectDid: String): Result<String> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val record =
                        encodeRecord(
                            record = Follow(createdAt = nowDatetime(), subject = Did(subjectDid)),
                            type = FOLLOW_NSID,
                        )
                    val response =
                        RepoService(client).createRecord(
                            CreateRecordRequest(
                                collection = Nsid(FOLLOW_NSID),
                                repo = AtIdentifier(viewerDid),
                                record = record,
                            ),
                        )
                    response.uri.raw
                }.onFailure { throwable ->
                    // runCatching swallows CancellationException; rethrow so a
                    // cancelled caller propagates structurally.
                    if (throwable is CancellationException) throw throwable
                    // `subjectDid` is PII (the followed account's DID); withhold
                    // it from the log, same policy as DefaultLikeRepostRepository.
                    Timber.tag(TAG).w(throwable, "follow failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun unfollow(followUri: String): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val (repo, rkey) = parseFollowUri(AtUri(followUri))
                    val client = xrpcClientProvider.authenticated()
                    RepoService(client).deleteRecord(
                        DeleteRecordRequest(
                            collection = Nsid(FOLLOW_NSID),
                            repo = repo,
                            rkey = rkey,
                        ),
                    )
                    Unit
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    // `followUri` carries the viewer's DID — keep it out of the
                    // log surface for the same reason DefaultLikeRepostRepository
                    // redacts its delete URIs.
                    Timber.tag(TAG).w(throwable, "unfollow failed: %s", throwable.javaClass.name)
                }
            }

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private fun nowDatetime(): Datetime = Datetime(Clock.System.now().toString())

        // We use `parseOrNull` + a local require() rather than upstream
        // `parse()` because parse()'s IllegalArgumentException message
        // includes the raw URI, which carries the viewer's DID. Same
        // redaction policy as DefaultLikeRepostRepository.parseAtUri.
        private fun parseFollowUri(uri: AtUri): Pair<AtIdentifier, RecordKey> {
            val parts =
                requireNotNull(uri.parseOrNull()) {
                    "follow AT URI is not structurally valid"
                }
            val rkey =
                requireNotNull(parts.rkey) {
                    "follow AT URI must be at://<repo>/app.bsky.graph.follow/<rkey>"
                }
            return parts.repo to rkey
        }

        private companion object {
            const val FOLLOW_NSID = "app.bsky.graph.follow"
            const val TAG = "FollowRepository"
        }
    }
