package net.kikin.nubecita.core.posting.internal

import io.github.kikin81.atproto.app.bsky.embed.AspectRatio
import io.github.kikin81.atproto.app.bsky.embed.External
import io.github.kikin81.atproto.app.bsky.embed.ExternalExternal
import io.github.kikin81.atproto.app.bsky.embed.Gallery
import io.github.kikin81.atproto.app.bsky.embed.GalleryImage
import io.github.kikin81.atproto.app.bsky.embed.Images
import io.github.kikin81.atproto.app.bsky.embed.ImagesImage
import io.github.kikin81.atproto.app.bsky.embed.Record
import io.github.kikin81.atproto.app.bsky.embed.RecordWithMedia
import io.github.kikin81.atproto.app.bsky.embed.RecordWithMediaMediaUnion
import io.github.kikin81.atproto.app.bsky.feed.Post
import io.github.kikin81.atproto.app.bsky.feed.PostEmbedUnion
import io.github.kikin81.atproto.app.bsky.feed.PostReplyRef
import io.github.kikin81.atproto.app.bsky.feed.Postgate
import io.github.kikin81.atproto.app.bsky.feed.PostgateDisableRule
import io.github.kikin81.atproto.app.bsky.feed.PostgateEmbeddingRulesUnion
import io.github.kikin81.atproto.app.bsky.feed.Threadgate
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateAllowUnion
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateFollowerRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateFollowingRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateMentionRule
import io.github.kikin81.atproto.com.atproto.repo.CreateRecordRequest
import io.github.kikin81.atproto.com.atproto.repo.RepoService
import io.github.kikin81.atproto.runtime.AtField
import io.github.kikin81.atproto.runtime.AtIdentifier
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Blob
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Language
import io.github.kikin81.atproto.runtime.Nsid
import io.github.kikin81.atproto.runtime.RecordKey
import io.github.kikin81.atproto.runtime.Uri
import io.github.kikin81.atproto.runtime.encodeRecord
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.CreatePost
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.image.ImageByteSource
import net.kikin.nubecita.core.image.ImageDimensionDecoder
import net.kikin.nubecita.core.image.ImageDimensions
import net.kikin.nubecita.core.image.ImageEncoder
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.ExternalLinkMetadataRepository
import net.kikin.nubecita.core.posting.LinkPreview
import net.kikin.nubecita.core.posting.LocaleProvider
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.PostingRepository
import net.kikin.nubecita.core.posting.ReplyAudience
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
        private val byteSource: ImageByteSource,
        private val encoder: ImageEncoder,
        private val dimensionDecoder: ImageDimensionDecoder,
        private val facetExtractor: FacetExtractor,
        private val localeProvider: LocaleProvider,
        private val externalLinkMetadataRepository: ExternalLinkMetadataRepository,
        private val analytics: AnalyticsClient,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : PostingRepository {
        override suspend fun createPost(
            text: String,
            attachments: List<ComposerAttachment>,
            replyTo: ReplyRefs?,
            langs: List<String>?,
            audience: PostAudience,
            quote: io.github.kikin81.atproto.com.atproto.repo.StrongRef?,
            external: LinkPreview?,
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
                    val uploaded =
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

                    // Phase 2b — external link card. Mutually exclusive with images
                    // (images win the media slot), so only prepared when there are
                    // none. The thumbnail download + upload is best-effort: any
                    // failure yields a card with no thumb rather than failing the post.
                    val preparedExternal =
                        if (external != null && uploaded.isEmpty()) prepareExternal(repo, external) else null

                    // Phase 3 — record creation. Only runs after every
                    // blob upload completed successfully.
                    val embed =
                        resolveEmbed(
                            ComposerEmbedIntent(images = uploaded, quote = quote, external = preparedExternal),
                        )
                    Timber.tag(TAG).d(
                        "createPost() — building record with embed=%s (images.size=%d)",
                        embed::class.simpleName,
                        uploaded.size,
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
                    // Fire-and-forget create_post on a confirmed write. Only
                    // structural booleans — never the body, language, or
                    // attachment URIs.
                    analytics.log(
                        CreatePost(
                            hasMedia = attachments.isNotEmpty(),
                            isReply = replyTo != null,
                            isQuote = quote != null,
                        ),
                    )

                    // Best-effort audience gates — TOP-LEVEL posts only. A
                    // threadgate's rkey must match the thread ROOT's rkey, so it's
                    // meaningless to attach one to a reply (whose rkey is its own,
                    // not the root's); we skip gates entirely on replies.
                    // [applyAudienceGates] self-handles each gate's failure (the post
                    // is already live; a write failure must never fail it); only
                    // cancellation propagates, into the main catch below.
                    if (replyTo == null) {
                        applyAudienceGates(repo, did, response.uri, audience)
                    }

                    Result.success(response.uri)
                } catch (cancellation: CancellationException) {
                    Timber.tag(TAG).d("createPost() — cancelled, re-throwing")
                    throw cancellation
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(throwable, "createPost() — failed in submit pipeline")
                    Result.failure(mapToComposerError(throwable))
                }
            }

        /**
         * Write the [audience]'s `app.bsky.feed.threadgate` / `app.bsky.feed.postgate`
         * records at the new post's rkey (both records must share the post's rkey,
         * per the lexicon). [PostAudience.DEFAULT] writes neither. Following the
         * lexicon: [ReplyAudience.Everyone] omits the threadgate entirely (anyone
         * replies), [ReplyAudience.Nobody] writes an **empty** `allow` (no one), a
         * combination writes the matching rules; quotes-off writes a postgate with
         * the disable-embedding rule.
         *
         * The two writes run in PARALLEL and are **independently** best-effort: a
         * failure of one is logged (error identity only) and swallowed so it neither
         * skips the other nor fails the already-live post. Only cancellation escapes.
         */
        private suspend fun applyAudienceGates(
            repo: RepoService,
            did: String,
            postUri: AtUri,
            audience: PostAudience,
        ) {
            val needsThreadgate = audience.reply != ReplyAudience.Everyone
            val needsPostgate = !audience.allowQuotes
            if (!needsThreadgate && !needsPostgate) return

            val repoId = AtIdentifier(did)
            val rkey = AtField.Defined(RecordKey(postUri.raw.substringAfterLast('/')))
            val createdAt = Datetime(Clock.System.now().toString())

            coroutineScope {
                val threadgate =
                    if (needsThreadgate) {
                        async {
                            bestEffortGate("threadgate") {
                                val record =
                                    Threadgate(
                                        allow = AtField.Defined(threadgateAllowRules(audience.reply)),
                                        createdAt = createdAt,
                                        post = postUri,
                                    )
                                repo.createRecord(
                                    CreateRecordRequest(
                                        repo = repoId,
                                        collection = Nsid("app.bsky.feed.threadgate"),
                                        rkey = rkey,
                                        record = encodeRecord(Threadgate.serializer(), record, "app.bsky.feed.threadgate"),
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    }
                val postgate =
                    if (needsPostgate) {
                        async {
                            bestEffortGate("postgate") {
                                val record =
                                    Postgate(
                                        createdAt = createdAt,
                                        embeddingRules =
                                            AtField.Defined(listOf<PostgateEmbeddingRulesUnion>(PostgateDisableRule())),
                                        post = postUri,
                                    )
                                repo.createRecord(
                                    CreateRecordRequest(
                                        repo = repoId,
                                        collection = Nsid("app.bsky.feed.postgate"),
                                        rkey = rkey,
                                        record = encodeRecord(Postgate.serializer(), record, "app.bsky.feed.postgate"),
                                    ),
                                )
                            }
                        }
                    } else {
                        null
                    }
                threadgate?.await()
                postgate?.await()
            }
        }

        // Runs one gate write, swallowing a failure (logged by error identity only,
        // per the repo's redaction policy) so it can't fail the live post or skip a
        // sibling gate. Cancellation still propagates.
        private suspend fun bestEffortGate(
            name: String,
            write: suspend () -> Unit,
        ) {
            try {
                write()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Timber.tag(TAG).w(
                    "createPost() — %s write failed (post still created): %s",
                    name,
                    throwable.javaClass.name,
                )
            }
        }

        // Maps the reply audience to threadgate allow rules. Nobody → empty list
        // (no one); a combination → exactly the checked groups' rules. Everyone is
        // unreachable here (the caller omits the threadgate entirely).
        private fun threadgateAllowRules(reply: ReplyAudience): List<ThreadgateAllowUnion> =
            when (reply) {
                ReplyAudience.Everyone, ReplyAudience.Nobody -> emptyList()
                is ReplyAudience.Combination ->
                    buildList {
                        if (reply.followers) add(ThreadgateFollowerRule())
                        if (reply.following) add(ThreadgateFollowingRule())
                        if (reply.mentioned) add(ThreadgateMentionRule())
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
            // Decode intrinsic dimensions from the ORIGINAL bytes (bounds-only,
            // no pixel allocation) for the embed's aspectRatio. Source bytes,
            // not the encoded variant, so the ratio is right even if the encoder
            // downscales — and it's available even when the encoder passes the
            // bytes through without decoding.
            val dimensions = dimensionDecoder.decode(raw)
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
            UploadedImage(blob = response.blob, alt = attachment.alt, dimensions = dimensions)
        } catch (cancellation: CancellationException) {
            // Cancellation propagates unchanged — required by structured
            // concurrency so siblings cancelled by `awaitAll`'s first-
            // failure semantics tear down cleanly.
            throw cancellation
        } catch (t: Throwable) {
            // Wrap with the attachment index so the UI can highlight
            // which row failed.
            Timber.tag(TAG).w(t, "uploadOne(#%d) — threw; wrapping as ComposerError.UploadFailed", index)
            throw ComposerError.UploadFailed(attachmentIndex = index, cause = t)
        }

        /**
         * The single embed-construction seam. Maps an [embed intent][ComposerEmbedIntent]
         * — the uploaded image blobs and/or a quoted-post [StrongRef] — to exactly one
         * `app.bsky.feed.post` embed variant. This is the ONLY place a [PostEmbedUnion]
         * is built, so a future embed type (e.g. GIF / external link) is added here and
         * in [ComposerEmbedIntent], never by introducing a second composer or a parallel
         * write path.
         *
         * | images | quote | external | embed |
         * |--------|-------|----------|-------|
         * | ∅ | ∅ | ∅ | none (`AtField.Missing`) |
         * | yes | * | * | `images` (or `recordWithMedia` w/ quote) — external dropped |
         * | ∅ | ∅ | yes | `app.bsky.embed.external` |
         * | ∅ | yes | ∅ | `app.bsky.embed.record` |
         * | ∅ | yes | yes | `app.bsky.embed.recordWithMedia` (external media) |
         */
        private fun resolveEmbed(intent: ComposerEmbedIntent): AtField<PostEmbedUnion> {
            // Interop rule (mirrors social-app): emit app.bsky.embed.images for
            // 1..4 images (maximum compatibility — a gallery is invisible on
            // clients without gallery support) and app.bsky.embed.gallery for 5+
            // (the composer caps adds at the soft limit of 10). Images win the
            // media slot over an external card (Bluesky forbids both); a card is
            // only the media when there are no images. Images, Gallery, and
            // External all implement RecordWithMediaMediaUnion (and PostEmbedUnion),
            // so the same value serves the standalone and quote+media branches.
            val external = intent.external
            val media: RecordWithMediaMediaUnion? =
                when {
                    intent.images.size in 1..LEGACY_IMAGES_EMBED_MAX ->
                        Images(images = intent.images.map { it.toImagesImage() })
                    intent.images.isNotEmpty() ->
                        Gallery(items = intent.images.map { it.toGalleryImage() })
                    // No images → an external card (if any) takes the media slot.
                    external != null -> External(external = external.toExternalExternal())
                    else -> null
                }
            val quote = intent.quote
            // Positive `!= null` ordering so each branch smart-casts — no `!!`.
            return when {
                quote != null && media != null ->
                    AtField.Defined(RecordWithMedia(record = Record(record = quote), media = media))
                quote != null -> AtField.Defined(Record(record = quote))
                // Images / Gallery / External all implement PostEmbedUnion; the
                // cross-cast is always valid for the types produced above.
                media != null -> AtField.Defined(media as PostEmbedUnion)
                else -> AtField.Missing
            }
        }

        /** Build the `app.bsky.embed.external#external` record from a [PreparedExternal]. */
        private fun PreparedExternal.toExternalExternal(): ExternalExternal =
            ExternalExternal(
                uri = Uri(uri),
                title = title,
                description = description,
                thumb = thumb,
            )

        /**
         * Resolve [external] into a [PreparedExternal] with its thumbnail uploaded
         * **best-effort**: download the preview image, re-encode (the same
         * `encodeForUpload` compression the image attachments use), and `uploadBlob`.
         * Any failure — no image, download, encode, or upload — yields
         * `thumb = AtField.Missing`, and the post still carries the card's
         * uri/title/description. A thumbnail never fails the post.
         */
        private suspend fun prepareExternal(
            repo: RepoService,
            external: LinkPreview,
        ): PreparedExternal {
            val thumb: AtField<Blob> =
                try {
                    // downloadThumb is bounded by the metadata repo's own request
                    // timeout (CardyB: 5 s), so a slow preview host can't hold up the
                    // post — best-effort, so a timeout just means no thumbnail.
                    val downloaded = external.imageUrl?.let { externalLinkMetadataRepository.downloadThumb(it) }
                    if (downloaded == null) {
                        AtField.Missing
                    } else {
                        val encoded =
                            encoder.encodeForUpload(bytes = downloaded.bytes, sourceMimeType = downloaded.mimeType)
                        val blob =
                            repo
                                .uploadBlob(
                                    input = encoded.bytes,
                                    inputContentType = ContentType.parse(encoded.mimeType),
                                ).blob
                        AtField.Defined(blob)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Timber.tag(TAG).d(e, "external thumbnail prep failed → posting card without thumb")
                    AtField.Missing
                }
            return PreparedExternal(
                uri = external.uri,
                title = external.title,
                description = external.description,
                thumb = thumb,
            )
        }

        /**
         * images#image carries an OPTIONAL aspectRatio (AtField). Non-positive
         * dimensions (defensive — a 0/negative would render as NaN/Infinity and
         * crash Modifier.aspectRatio) drop to Missing so the render layer uses
         * its own fallback aspect.
         */
        private fun UploadedImage.toImagesImage(): ImagesImage =
            ImagesImage(
                alt = alt,
                image = blob,
                aspectRatio = dimensions.toAspectRatioOrNull()?.let { AtField.Defined(it) } ?: AtField.Missing,
            )

        /**
         * gallery#image REQUIRES a non-null aspectRatio. When dimensions are
         * absent or non-positive (rare — corrupt bytes), fall back to 1:1 so the
         * record is still valid and never carries a degenerate ratio; a square
         * is a neutral default.
         */
        private fun UploadedImage.toGalleryImage(): GalleryImage =
            GalleryImage(
                alt = alt,
                image = blob,
                aspectRatio = dimensions.toAspectRatioOrNull() ?: AspectRatio(width = 1L, height = 1L),
            )

        /** A wire [AspectRatio] only when both dimensions are strictly positive. */
        private fun ImageDimensions?.toAspectRatioOrNull(): AspectRatio? =
            this
                ?.takeIf { it.width > 0 && it.height > 0 }
                ?.let { AspectRatio(width = it.width.toLong(), height = it.height.toLong()) }

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

            /**
             * Largest image count still emitted as `app.bsky.embed.images`. At
             * 5+ the repository switches to `app.bsky.embed.gallery`. Matches the
             * lexicon's images cap and social-app's LEGACY_IMAGES_EMBED_MAX.
             */
            private const val LEGACY_IMAGES_EMBED_MAX = 4

            // The JVM returns this sentinel from `Locale.forLanguageTag`
            // for any input that doesn't parse as a BCP-47 language tag
            // (`""`, `"!!"`, etc.). Round-tripping a valid input always
            // returns the normalized form of the tag, never `und`, so
            // this is a reliable validity check without dragging in a
            // full BCP-47 parser.
            private const val UNDETERMINED_LANGUAGE_TAG = "und"
        }
    }
