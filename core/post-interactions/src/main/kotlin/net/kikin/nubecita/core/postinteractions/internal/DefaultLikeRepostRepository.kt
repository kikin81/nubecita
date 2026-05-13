package net.kikin.nubecita.core.postinteractions.internal

import io.github.kikin81.atproto.app.bsky.feed.Like
import io.github.kikin81.atproto.app.bsky.feed.Repost
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.DeleteRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.encodeRecord
import io.github.kikin81.atproto.runtime.parseOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.postinteractions.LikeRepostRepository
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

internal class DefaultLikeRepostRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : LikeRepostRepository {
        override suspend fun like(post: StrongRef): Result<AtUri> = createRecord(LIKE_NSID, post) { createdAt, subject -> Like(createdAt = createdAt, subject = subject) }

        override suspend fun unlike(likeUri: AtUri): Result<Unit> = deleteRecord(LIKE_NSID, likeUri)

        override suspend fun repost(post: StrongRef): Result<AtUri> = createRecord(REPOST_NSID, post) { createdAt, subject -> Repost(createdAt = createdAt, subject = subject) }

        override suspend fun unrepost(repostUri: AtUri): Result<Unit> = deleteRecord(REPOST_NSID, repostUri)

        // Both like and repost share the same shape: serialize a typed record
        // with $type, send via createRecord, return the new record's uri.
        // The caller passes a builder that closes over the record's typed
        // representation so we keep the per-collection NSID in one place.
        private suspend inline fun <reified T> createRecord(
            collection: String,
            subject: StrongRef,
            crossinline buildRecord: (createdAt: Datetime, subject: StrongRef) -> T,
        ): Result<AtUri> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val record =
                        encodeRecord(
                            record = buildRecord(nowDatetime(), subject),
                            type = collection,
                        )
                    val response =
                        RepoService(client).createRecord(
                            CreateRecordRequest(
                                collection = Nsid(collection),
                                repo = AtIdentifier(viewerDid),
                                record = record,
                            ),
                        )
                    response.uri
                }.onFailure { throwable ->
                    // Surface the throwable identity for observability — same
                    // rationale as DefaultFeedRepository. The atproto stack
                    // wraps engine failures, and without this log we'd be
                    // guessing whether a "create-record failed" effect came
                    // from a network blip, an auth refresh failure, or a
                    // server-side validation error.
                    Timber.tag(TAG).e(
                        throwable,
                        "createRecord(%s) failed: %s",
                        collection,
                        throwable.javaClass.name,
                    )
                }
            }

        private suspend fun deleteRecord(
            collection: String,
            recordUri: AtUri,
        ): Result<Unit> =
            withContext(dispatcher) {
                runCatching {
                    val (repo, rkey) = parseAtUri(recordUri)
                    val client = xrpcClientProvider.authenticated()
                    RepoService(client).deleteRecord(
                        DeleteRecordRequest(
                            collection = Nsid(collection),
                            repo = repo,
                            rkey = rkey,
                        ),
                    )
                    Unit
                }.onFailure { throwable ->
                    // Mirror DefaultXrpcClientProvider's redaction policy — the
                    // recordUri carries the viewer's DID, which we keep out of
                    // log surfaces that may be captured by a future release
                    // crash reporter. The throwable's stack carries the
                    // underlying server / network cause; collection is enough
                    // to disambiguate which path failed.
                    Timber.tag(TAG).e(
                        throwable,
                        "deleteRecord(%s) failed: %s",
                        collection,
                        throwable.javaClass.name,
                    )
                }
            }

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private fun nowDatetime(): Datetime = Datetime(Clock.System.now().toString())

        // The repository intentionally does NOT validate the collection
        // segment against the expected NSID — the caller is responsible for
        // passing a uri that belongs to the right collection (the like uri
        // to unlike, the repost uri to unrepost). A wrong-collection uri
        // would be rejected by the PDS and surface as a failure, which is
        // the right outcome.
        //
        // Fragments (`#...`) are stripped by the upstream parser — record
        // URIs don't address sub-records, and a fragment-bearing rkey would
        // be rejected by the PDS. Stripping is semantically safe (the same
        // record is referenced regardless of fragment).
        //
        // We use `parseOrNull` + a local require() rather than upstream
        // `parse()` because parse()'s IllegalArgumentException message
        // includes the raw URI, which carries the viewer's DID — see the
        // redaction note on the deleteRecord onFailure log.
        private fun parseAtUri(uri: AtUri): Pair<AtIdentifier, RecordKey> {
            val parts =
                requireNotNull(uri.parseOrNull()) {
                    "AT URI is not structurally valid"
                }
            val rkey =
                requireNotNull(parts.rkey) {
                    "AT URI must be exactly at://<repo>/<collection>/<rkey>"
                }
            return parts.repo to rkey
        }

        private companion object {
            const val LIKE_NSID = "app.bsky.feed.like"
            const val REPOST_NSID = "app.bsky.feed.repost"
            const val TAG = "LikeRepostRepository"
        }
    }
