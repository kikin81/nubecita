package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.app.bsky.embed.Images
import io.github.kikin81.atproto.app.bsky.embed.ImagesImage
import io.github.kikin81.atproto.app.bsky.feed.Post
import io.github.kikin81.atproto.app.bsky.feed.PostEmbedUnion
import io.github.kikin81.atproto.app.bsky.feed.PostReplyRef
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.encodeRecord
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyRefs
import java.io.IOException
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Production [PostingRepository] backed by the atproto-kotlin SDK.
 *
 * Submission flow:
 *
 * 1. Resolve the active session DID from [SessionStateProvider]. No
 *    session ⇒ `ComposerError.Unauthorized` without touching the
 *    network.
 * 2. (When attachments are present) Read each attachment's bytes and
 *    upload via `RepoService.uploadBlob(bytes, contentType)` in
 *    parallel inside a single `coroutineScope { ... awaitAll() }`.
 *    Wall-clock submission time is bounded by the slowest upload, not
 *    the sum. The first upload failure cancels its siblings and
 *    aborts the whole submit with `ComposerError.UploadFailed(index, cause)`
 *    — no partial-record creation.
 * 3. Construct the `app.bsky.feed.post` record with optional `reply`
 *    (when [ReplyRefs] is non-null) and optional `embed.images`
 *    (when blob CIDs were collected). Alt text is empty for V1 — the
 *    composer's UI doesn't yet expose alt-text editing (separate
 *    follow-up).
 * 4. Submit via `RepoService.createRecord(...)` and return the
 *    response's `uri`.
 *
 * All I/O is dispatched on [IoDispatcher]. Failures are mapped to
 * typed [ComposerError]s and returned via `kotlin.Result`'s exception
 * channel — the caller (composer ViewModel) unwraps with
 * `result.exceptionOrNull() as? ComposerError`.
 */
@OptIn(ExperimentalTime::class)
internal class DefaultPostingRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        private val byteSource: AttachmentByteSource,
        private val encoder: AttachmentEncoder,
        private val facetExtractor: FacetExtractor,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostingRepository {
        override suspend fun createPost(
            text: String,
            attachments: List<ComposerAttachment>,
            replyTo: ReplyRefs?,
        ): Result<AtUri> =
            withContext(dispatcher) {
                val did =
                    when (val state = sessionStateProvider.state.value) {
                        is SessionState.SignedIn -> state.did
                        else -> return@withContext Result.failure(ComposerError.Unauthorized)
                    }

                // Explicit try/catch (not runCatching) so CancellationException
                // propagates unchanged — runCatching would convert
                // coroutine cancellation into a Result.failure and
                // break structured concurrency for callers that cancel
                // the submit (e.g., user backs out mid-upload).
                try {
                    val client = xrpcClientProvider.authenticated()
                    val repo = RepoService(client)

                    // Phase 1 — parallel blob uploads. The first failure
                    // throws ComposerError.UploadFailed(index, cause)
                    // out of the coroutineScope, cancelling siblings.
                    val blobs =
                        if (attachments.isEmpty()) {
                            emptyList()
                        } else {
                            coroutineScope {
                                attachments
                                    .mapIndexed { index, attachment ->
                                        async {
                                            uploadOne(repo, index, attachment)
                                        }
                                    }.awaitAll()
                            }
                        }

                    // Phase 2 — facet extraction. Parses the composer
                    // text for `@handle` mentions and `https://…` URLs
                    // and resolves each handle to its canonical DID
                    // via `com.atproto.identity.resolveHandle`.
                    // Unresolvable handles are silently dropped per
                    // the AT Protocol docs — they render as plain text
                    // on Bluesky's appview rather than failing the
                    // whole submit.
                    val facets = facetExtractor.extract(text)

                    // Phase 3 — record creation. Only runs after every
                    // blob upload completed successfully.
                    val record =
                        Post(
                            text = text,
                            createdAt = Datetime(Clock.System.now().toString()),
                            reply =
                                replyTo
                                    ?.let { AtField.Defined(PostReplyRef(parent = it.parent, root = it.root)) }
                                    ?: AtField.Missing,
                            embed = embedFor(blobs),
                            facets =
                                if (facets.isEmpty()) {
                                    AtField.Missing
                                } else {
                                    AtField.Defined(facets)
                                },
                        )

                    val response =
                        repo.createRecord(
                            CreateRecordRequest(
                                repo = AtIdentifier(did),
                                collection = Nsid("app.bsky.feed.post"),
                                record = encodeRecord(Post.serializer(), record, "app.bsky.feed.post"),
                            ),
                        )
                    Result.success(response.uri)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (throwable: Throwable) {
                    Result.failure(mapToComposerError(throwable))
                }
            }

        private suspend fun uploadOne(
            repo: RepoService,
            index: Int,
            attachment: ComposerAttachment,
        ) = try {
            val raw = byteSource.read(attachment.uri)
            // Image compression sits between read and uploadBlob: any
            // photo over Bluesky's per-blob byte cap is re-encoded
            // (typically as WebP) by the encoder; bytes already under
            // the cap pass through untouched. Without this step a
            // typical phone photo (>1 MB) would silently fail at
            // uploadBlob with no remedy in-app.
            val encoded = encoder.encodeForUpload(bytes = raw, sourceMimeType = attachment.mimeType)
            val response =
                repo.uploadBlob(
                    input = encoded.bytes,
                    inputContentType = ContentType.parse(encoded.mimeType),
                )
            response.blob
        } catch (cancellation: CancellationException) {
            // Cancellation propagates unchanged — required by structured
            // concurrency so siblings cancelled by `awaitAll`'s first-
            // failure semantics tear down cleanly.
            throw cancellation
        } catch (t: Throwable) {
            // Wrap with the attachment index so the UI can highlight
            // which row failed.
            throw ComposerError.UploadFailed(attachmentIndex = index, cause = t)
        }

        private fun embedFor(blobs: List<io.github.kikin81.atproto.runtime.Blob>): AtField<PostEmbedUnion> =
            if (blobs.isEmpty()) {
                AtField.Missing
            } else {
                AtField.Defined(
                    Images(
                        images = blobs.map { ImagesImage(alt = "", image = it) },
                    ),
                )
            }

        /**
         * Maps a raw throwable from the SDK or coroutine machinery to
         * the typed [ComposerError] surface so the public API contract
         * (every documented variant is actually producible) holds.
         *
         * Order matters — already-typed [ComposerError]s pass through
         * first, then SDK-typed [NoSessionException] (the auth gap that
         * `XrpcClientProvider.authenticated()` raises when the session
         * is missing or its tokens couldn't be refreshed), then
         * transport-layer [IOException] (Ktor's `IOException` wrapper
         * for socket/DNS/timeout failures). Anything else is a
         * server-side rejection from `createRecord` (rate-limit,
         * content-policy, malformed record) and surfaces as
         * [ComposerError.RecordCreationFailed].
         *
         * `ComposerError.ParentNotFound` is currently *not* produced
         * here — the V1 composer's reply-mode flow resolves the parent
         * via `getPostThread` (in `:feature:composer:impl`) before
         * submitting, so a missing-parent failure surfaces at fetch
         * time rather than at submit time. If a future PDS surface
         * surfaces "parent deleted between fetch and submit" as a
         * specific XRPC error code, this mapper grows a clause for it.
         */
        private fun mapToComposerError(throwable: Throwable): ComposerError =
            when (throwable) {
                is ComposerError -> throwable
                is NoSessionException -> ComposerError.Unauthorized
                is IOException -> ComposerError.Network(throwable)
                else -> ComposerError.RecordCreationFailed(throwable)
            }
    }
