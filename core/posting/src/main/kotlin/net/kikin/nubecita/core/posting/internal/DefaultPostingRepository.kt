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
import io.github.kikin81.atproto.runtime.Language
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
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyRefs
import timber.log.Timber
import java.io.IOException
import java.util.Locale
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
        private val localeProvider: LocaleProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostingRepository {
        override suspend fun createPost(
            text: String,
            attachments: List<ComposerAttachment>,
            replyTo: ReplyRefs?,
            langs: List<String>?,
        ): Result<AtUri> =
            withContext(dispatcher) {
                // `replyTo` is a typed ReplyRefs(parent, root); both
                // refs carry full AT-URIs containing third-party DIDs.
                // Per the repo's redaction policy (see :core:auth
                // DefaultXrpcClientProvider.redactDid + the rkey-only
                // postdetail logging), don't put the raw refs on a
                // Timber surface — log a presence boolean instead. If
                // a reply submission fails, the parent identity is
                // recoverable from the createRecord request payload
                // when network logging is enabled, but we don't need
                // it on every entry breadcrumb.
                Timber.tag(TAG).d(
                    "createPost() entry — text.len=%d, attachments=%d, hasReply=%b",
                    text.length,
                    attachments.size,
                    replyTo != null,
                )
                val did =
                    when (val state = sessionStateProvider.state.value) {
                        is SessionState.SignedIn -> state.did
                        else -> {
                            Timber.tag(TAG).w("createPost() — no signed-in session, returning Unauthorized")
                            return@withContext Result.failure(ComposerError.Unauthorized)
                        }
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
                            Timber.tag(TAG).d("createPost() — no attachments, skipping uploadBlob phase")
                            emptyList()
                        } else {
                            Timber.tag(TAG).d("createPost() — starting %d parallel uploadBlob calls", attachments.size)
                            coroutineScope {
                                attachments
                                    .mapIndexed { index, attachment ->
                                        async {
                                            uploadOne(repo, index, attachment)
                                        }
                                    }.awaitAll()
                            }.also {
                                Timber.tag(TAG).d("createPost() — all %d blob uploads complete", it.size)
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
                    val embed = embedFor(blobs)
                    Timber.tag(TAG).d(
                        "createPost() — building record with embed=%s (blobs.size=%d)",
                        embed::class.simpleName,
                        blobs.size,
                    )
                    val resolvedLangs = resolveLangs(langs)
                    Timber.tag(TAG).d(
                        "createPost() — resolvedLangs=%s (caller=%s)",
                        resolvedLangs.joinToString(),
                        if (langs == null) "null/default" else "explicit(${langs.size})",
                    )
                    val record =
                        Post(
                            text = text,
                            createdAt = Datetime(Clock.System.now().toString()),
                            reply =
                                replyTo
                                    ?.let { AtField.Defined(PostReplyRef(parent = it.parent, root = it.root)) }
                                    ?: AtField.Missing,
                            embed = embed,
                            facets =
                                if (facets.isEmpty()) {
                                    AtField.Missing
                                } else {
                                    AtField.Defined(facets)
                                },
                            langs =
                                if (resolvedLangs.isEmpty()) {
                                    AtField.Missing
                                } else {
                                    AtField.Defined(resolvedLangs.map(::Language))
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
                    // The full AtUri carries the signed-in user's DID
                    // (`at://did:plc:.../app.bsky.feed.post/<rkey>`).
                    // Log just the rkey — same redaction shape used by
                    // :feature:postdetail:impl's PostDetailScreen and
                    // PostThreadRepository. The rkey alone identifies
                    // the just-created post within the user's repo for
                    // diagnostics; the DID isn't needed on this path.
                    Timber.tag(TAG).d(
                        "createPost() — createRecord ok, rkey=%s",
                        response.uri.raw.substringAfterLast('/'),
                    )
                    Result.success(response.uri)
                } catch (cancellation: CancellationException) {
                    Timber.tag(TAG).d("createPost() — cancelled, re-throwing")
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).e(throwable, "createPost() — failed in submit pipeline")
                    Result.failure(mapToComposerError(throwable))
                }
            }

        private suspend fun uploadOne(
            repo: RepoService,
            index: Int,
            attachment: ComposerAttachment,
        ) = try {
            val raw = byteSource.read(attachment.uri)
            Timber.tag(TAG).d(
                "uploadOne(#%d) — read %d raw bytes, mime=%s",
                index,
                raw.size,
                attachment.mimeType,
            )
            // Image compression sits between read and uploadBlob: any
            // photo over Bluesky's per-blob byte cap is re-encoded
            // (typically as WebP) by the encoder; bytes already under
            // the cap pass through untouched. Without this step a
            // typical phone photo (>1 MB) would silently fail at
            // uploadBlob with no remedy in-app.
            val encoded = encoder.encodeForUpload(bytes = raw, sourceMimeType = attachment.mimeType)
            Timber.tag(TAG).d(
                "uploadOne(#%d) — encoded to %d bytes, mime=%s, uploading…",
                index,
                encoded.bytes.size,
                encoded.mimeType,
            )
            val response =
                repo.uploadBlob(
                    input = encoded.bytes,
                    inputContentType = ContentType.parse(encoded.mimeType),
                )
            Timber.tag(TAG).d("uploadOne(#%d) — ok, blob.size=%d", index, response.blob.size)
            response.blob
        } catch (cancellation: CancellationException) {
            // Cancellation propagates unchanged — required by structured
            // concurrency so siblings cancelled by `awaitAll`'s first-
            // failure semantics tear down cleanly.
            throw cancellation
        } catch (t: Throwable) {
            // Wrap with the attachment index so the UI can highlight
            // which row failed.
            Timber.tag(TAG).e(t, "uploadOne(#%d) — threw; wrapping as ComposerError.UploadFailed", index)
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

        /**
         * Decide which BCP-47 tags actually go on the record. `null`
         * means the caller wants the device-locale default; any other
         * input is taken verbatim modulo BCP-47 validity. Validation
         * round-trips each tag through `Locale.forLanguageTag` and drops
         * anything that resolves to `und` (the JVM's "undetermined"
         * sentinel for unparseable input). The result may be empty —
         * the caller of this helper is responsible for translating that
         * into `AtField.Missing` rather than `AtField.Defined(emptyList())`,
         * since the lexicon's `langs` field only carries non-empty arrays.
         */
        private fun resolveLangs(callerLangs: List<String>?): List<String> {
            val raw = callerLangs ?: listOf(localeProvider.primaryLanguageTag())
            return raw.filter(::isValidBcp47Tag)
        }

        private fun isValidBcp47Tag(tag: String): Boolean {
            if (tag.isBlank()) return false
            val roundTripped = Locale.forLanguageTag(tag).toLanguageTag()
            return roundTripped != UNDETERMINED_LANGUAGE_TAG
        }

        companion object {
            private const val TAG = "PostingRepo"

            // The JVM returns this sentinel from `Locale.forLanguageTag`
            // for any input that doesn't parse as a BCP-47 language tag
            // (`""`, `"!!"`, etc.). Round-tripping a valid input always
            // returns the normalized form of the tag, never `und`, so
            // this is a reliable validity check without dragging in a
            // full BCP-47 parser.
            private const val UNDETERMINED_LANGUAGE_TAG = "und"
        }
    }
