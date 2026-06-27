package net.kikin.nubecita.core.posting.internal

import android.net.Uri
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetFeaturesUnion
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.CreatePost
import net.kikin.nubecita.core.analytics.UserProperty
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.image.EncodedImage
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
import net.kikin.nubecita.core.posting.ReplyAudience
import net.kikin.nubecita.core.posting.ReplyRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [DefaultPostingRepository].
 *
 * Strategy: stand up a real [XrpcClient] backed by a Ktor [MockEngine]
 * so the SDK's full `RepoService.uploadBlob(...)` →
 * `RepoService.createRecord(...)` codepath runs end-to-end against
 * deterministic responses. Asserts work on the recorded HTTP request
 * history (`engine.requestHistory`) and on captured request bodies.
 *
 * `android.net.Uri` is mockk-relaxed since the test's fake
 * [ImageByteSource] ignores the URI and returns canned bytes —
 * we just need a non-null [Uri] reference for [ComposerAttachment]'s
 * constructor.
 *
 * Coverage:
 *   - Happy path with text only (no blob uploads, single createRecord)
 *   - Parallel-then-serial ordering (3 attachments → all 3 uploadBlob
 *     calls complete before createRecord begins)
 *   - Blob upload failure aborts before createRecord (no createRecord
 *     call made; ComposerError.UploadFailed surfaced with the failing
 *     attachment index)
 *   - Reply mode carries both parent and root refs into the record's
 *     reply field
 *   - Unauthorized signed-out state short-circuits without touching
 *     the network
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPostingRepositoryTest {
    private val testDid = "did:plc:abc"

    // Recording analytics sink so create_post emission can be asserted.
    // A fresh instance per test method (JUnit instantiates the class per
    // test) keeps `events` isolated between cases.
    private val analytics = RecordingAnalyticsClient()

    @Test
    fun textOnlyPost_succeeds_andHitsCreateRecordOnce() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/abc","cid":"bafyfeed"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.createPost(text = "hello world", attachments = emptyList(), replyTo = null)

            assertTrue(result.isSuccess)
            assertEquals(AtUri("at://$testDid/app.bsky.feed.post/abc"), result.getOrNull())
            assertEquals(1, engine.requestHistory.size)
            assertTrue(
                engine.requestHistory
                    .single()
                    .url.encodedPath
                    .endsWith("createRecord"),
            )
        }

    @Test
    fun threeAttachments_allUploadsCompleteBeforeCreateRecord() =
        runTest {
            // Verifies the ordering contract from the spec: every
            // uploadBlob completes before createRecord begins. The
            // createRecord handler asserts the gate has fired, which
            // only happens once all 3 uploads have responded.
            //
            // Note on strict wall-clock parallelism: this test does
            // not pin "all 3 uploads suspended in flight at the same
            // instant" — Ktor's MockEngine handler runs on
            // Dispatchers.IO (real OS threads), not on the test
            // scheduler, so a `testScheduler.runCurrent()`-style sync
            // point can't observe their concurrent suspension
            // deterministically. The implementation contract that
            // matters is "uploads complete before createRecord" — i.e.
            // record creation never receives a partial blob set.
            // Whether the uploads physically overlap on the wire is an
            // implementation detail of `coroutineScope { ... awaitAll() }`
            // that the SDK's own tests cover.
            val createRecordGate = CompletableDeferred<Unit>()
            val uploadCount = AtomicInteger(0)

            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.url.encodedPath.endsWith("uploadBlob") -> {
                            val n = uploadCount.incrementAndGet()
                            if (n == 3) createRecordGate.complete(Unit)
                            okJson(
                                """{"blob":{"ref":{"${'$'}link":"bafblob$n"},"mimeType":"image/jpeg","size":3,"${'$'}type":"blob"}}""",
                            )
                        }
                        request.url.encodedPath.endsWith("createRecord") -> {
                            assertTrue(
                                createRecordGate.isCompleted,
                                "createRecord ran before all uploadBlobs finished",
                            )
                            okJson("""{"uri":"at://$testDid/app.bsky.feed.post/multi","cid":"bafmulti"}""")
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "post with images",
                    attachments =
                        listOf(
                            attachment("image/jpeg"),
                            attachment("image/jpeg"),
                            attachment("image/png"),
                        ),
                    replyTo = null,
                )

            assertTrue(result.isSuccess)
            assertEquals(3, uploadCount.get())
            // 3 uploads + 1 createRecord
            assertEquals(4, engine.requestHistory.size)
            assertTrue(
                engine.requestHistory
                    .last()
                    .url.encodedPath
                    .endsWith("createRecord"),
            )
            assertEquals(
                3,
                engine.requestHistory.count { it.url.encodedPath.endsWith("uploadBlob") },
            )
        }

    @Test
    fun fourAttachments_emitImagesEmbed() =
        runTest {
            // Interop: 1..4 images stay app.bsky.embed.images for maximum
            // compatibility (a gallery is invisible on clients without support).
            val body = captureCreatedRecordBody(attachmentCount = 4)
            assertTrue(body.contains("app.bsky.embed.images"), "expected images embed: $body")
            assertFalse(body.contains("app.bsky.embed.gallery"), "must not emit gallery at 4: $body")
        }

    @Test
    fun fiveAttachments_emitGalleryEmbed() =
        runTest {
            // Crossing 4 promotes to app.bsky.embed.gallery.
            val body = captureCreatedRecordBody(attachmentCount = 5)
            assertTrue(body.contains("app.bsky.embed.gallery"), "expected gallery embed: $body")
            assertFalse(body.contains("app.bsky.embed.images"), "must not emit images at 5: $body")
        }

    @Test
    fun galleryImages_carryAltTextAndAspectRatio() =
        runTest {
            // dimensionDecoder is fixed at 1200x800 -> aspectRatio width/height
            // must be serialized, and the per-image alt must be carried through.
            val body =
                captureCreatedRecordBody(
                    attachments = (0 until 5).map { attachment("image/jpeg", alt = "described $it") },
                )
            assertTrue(body.contains("app.bsky.embed.gallery"), "expected gallery embed: $body")
            assertTrue(body.contains("described 0"), "alt text missing: $body")
            assertTrue(body.contains("aspectRatio"), "aspectRatio missing: $body")
            assertTrue(body.contains("1200") && body.contains("800"), "aspect width/height missing: $body")
        }

    @Test
    fun quoteWithFiveImages_emitsRecordWithMediaGallery() =
        runTest {
            val quote =
                StrongRef(
                    uri = AtUri("at://did:plc:alice/app.bsky.feed.post/quoted"),
                    cid = Cid("bafquoted"),
                )
            val body = captureCreatedRecordBody(attachmentCount = 5, quote = quote)
            assertTrue(body.contains("app.bsky.embed.recordWithMedia"), "expected recordWithMedia: $body")
            assertTrue(body.contains("app.bsky.embed.gallery"), "expected gallery media: $body")
            assertTrue(body.contains("at://did:plc:alice/app.bsky.feed.post/quoted"), "quote uri missing: $body")
        }

    @Test
    fun imagesWithNonPositiveDimensions_omitAspectRatio() =
        runTest {
            // Defensive guard: a 0/negative dimension would render as NaN/Infinity
            // and crash Modifier.aspectRatio. On the images path the field is
            // optional, so it must be omitted entirely.
            val body = captureCreatedRecordBody(attachmentCount = 4, dimensionDecoder = fixedDimensions(width = 0, height = 0))
            assertTrue(body.contains("app.bsky.embed.images"), "expected images embed: $body")
            assertFalse(body.contains("aspectRatio"), "non-positive dims must omit aspectRatio: $body")
        }

    @Test
    fun galleryWithNonPositiveDimensions_fallsBackToSquareAspectRatio() =
        runTest {
            // gallery#image requires a non-null aspectRatio, so a degenerate
            // dimension falls back to 1:1 rather than being dropped.
            val body = captureCreatedRecordBody(attachmentCount = 5, dimensionDecoder = fixedDimensions(width = 0, height = 0))
            assertTrue(body.contains("app.bsky.embed.gallery"), "expected gallery embed: $body")
            assertTrue(body.contains("aspectRatio"), "gallery must still carry an aspectRatio: $body")
        }

    @Test
    fun blobUploadFailure_abortsBeforeCreateRecord() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.url.encodedPath.endsWith("uploadBlob") ->
                            respond(
                                ByteReadChannel("""{"error":"BlobTooLarge","message":"blob exceeds 2MB"}"""),
                                HttpStatusCode.BadRequest,
                                headersOf("Content-Type", ContentType.Application.Json.toString()),
                            )
                        request.url.encodedPath.endsWith("createRecord") ->
                            error("createRecord must not run after a blob upload failure")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "ok",
                    attachments = listOf(attachment("image/jpeg")),
                    replyTo = null,
                )

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull() as? ComposerError.UploadFailed
            assertNotNull(cause, "Expected ComposerError.UploadFailed, got: ${result.exceptionOrNull()}")
            assertEquals(0, cause!!.attachmentIndex)
            // Critically: no createRecord call was made.
            assertTrue(engine.requestHistory.none { it.url.encodedPath.endsWith("createRecord") })
        }

    @Test
    fun replyMode_carriesBothParentAndRootRefs() =
        runTest {
            val parentRef =
                StrongRef(
                    uri = AtUri("at://did:plc:alice/app.bsky.feed.post/parent"),
                    cid = Cid("bafparent"),
                )
            val rootRef =
                StrongRef(
                    uri = AtUri("at://did:plc:alice/app.bsky.feed.post/root"),
                    cid = Cid("bafroot"),
                )
            val capturedBody = CompletableDeferred<String>()

            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/reply","cid":"bafreply"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "this is a reply",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parentRef, root = rootRef),
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            // Both parent and root URIs + CIDs appear in the serialized
            // record's reply field. (We don't parse the JSON — substring
            // checks are sufficient and avoid coupling to the SDK's
            // exact serializer output shape.)
            assertTrue(body.contains("at://did:plc:alice/app.bsky.feed.post/parent"), "parent uri missing: $body")
            assertTrue(body.contains("at://did:plc:alice/app.bsky.feed.post/root"), "root uri missing: $body")
            assertTrue(body.contains("bafparent"), "parent cid missing: $body")
            assertTrue(body.contains("bafroot"), "root cid missing: $body")
        }

    @Test
    fun quoteOnly_writesRecordEmbed() =
        runTest {
            val quoteRef =
                StrongRef(
                    uri = AtUri("at://did:plc:bob/app.bsky.feed.post/quoted"),
                    cid = Cid("bafquoted"),
                )
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/q","cid":"bafq"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "quoting bob",
                    attachments = emptyList(),
                    replyTo = null,
                    quote = quoteRef,
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("app.bsky.embed.record"), "record embed missing: $body")
            assertTrue(!body.contains("recordWithMedia"), "plain record, not recordWithMedia: $body")
            assertTrue(body.contains("at://did:plc:bob/app.bsky.feed.post/quoted"), "quoted uri missing: $body")
            assertTrue(body.contains("bafquoted"), "quoted cid missing: $body")
        }

    @Test
    fun quotePlusImages_writesRecordWithMediaEmbed() =
        runTest {
            val quoteRef =
                StrongRef(
                    uri = AtUri("at://did:plc:bob/app.bsky.feed.post/quoted"),
                    cid = Cid("bafquoted"),
                )
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.url.encodedPath.endsWith("uploadBlob") ->
                            okJson(
                                """{"blob":{"ref":{"${'$'}link":"bafblobq"},"mimeType":"image/jpeg","size":3,"${'$'}type":"blob"}}""",
                            )
                        request.url.encodedPath.endsWith("createRecord") -> {
                            capturedBody.complete(request.body.toBodyString())
                            okJson("""{"uri":"at://$testDid/app.bsky.feed.post/qm","cid":"bafqm"}""")
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "quote with a picture",
                    attachments = listOf(attachment("image/jpeg")),
                    replyTo = null,
                    quote = quoteRef,
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("app.bsky.embed.recordWithMedia"), "recordWithMedia embed missing: $body")
            assertTrue(body.contains("at://did:plc:bob/app.bsky.feed.post/quoted"), "quoted uri missing: $body")
            assertTrue(body.contains("bafblobq"), "image blob missing from media: $body")
        }

    @Test
    fun imagesOnly_writesImagesEmbedNotRecord() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    when {
                        request.url.encodedPath.endsWith("uploadBlob") ->
                            okJson(
                                """{"blob":{"ref":{"${'$'}link":"bafblob1"},"mimeType":"image/jpeg","size":3,"${'$'}type":"blob"}}""",
                            )
                        request.url.encodedPath.endsWith("createRecord") -> {
                            capturedBody.complete(request.body.toBodyString())
                            okJson("""{"uri":"at://$testDid/app.bsky.feed.post/img","cid":"bafimg"}""")
                        }
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "just a photo",
                    attachments = listOf(attachment("image/jpeg")),
                    replyTo = null,
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("app.bsky.embed.images"), "images embed missing: $body")
            assertTrue(!body.contains("app.bsky.embed.record"), "images-only must not write a record embed: $body")
        }

    // ---- External link card (nubecita-gfli.3) ----

    @Test
    fun externalOnly_writesExternalEmbed() =
        runTest {
            val body = captureExternalRecordBody(aLinkPreview(imageUrl = null))
            assertTrue(body.contains("app.bsky.embed.external"), "external embed missing: $body")
            assertTrue(body.contains("https://example.com/article"), "external uri missing: $body")
            assertFalse(body.contains("app.bsky.embed.record"), "external-only must not write a record: $body")
            assertFalse(body.contains("app.bsky.embed.images"), "external-only must not write images: $body")
        }

    @Test
    fun externalPlusQuote_writesRecordWithMedia() =
        runTest {
            val quote = StrongRef(uri = AtUri("at://did:plc:bob/app.bsky.feed.post/quoted"), cid = Cid("bafquoted"))
            val body = captureExternalRecordBody(aLinkPreview(imageUrl = null), quote = quote)
            assertTrue(body.contains("app.bsky.embed.recordWithMedia"), "recordWithMedia missing: $body")
            assertTrue(body.contains("app.bsky.embed.external"), "external media missing: $body")
        }

    @Test
    fun externalPlusImages_dropsExternal_imagesWin() =
        runTest {
            val body =
                captureExternalRecordBody(
                    aLinkPreview(imageUrl = null),
                    attachments = listOf(attachment("image/jpeg")),
                )
            assertTrue(body.contains("app.bsky.embed.images"), "images embed missing: $body")
            assertFalse(body.contains("app.bsky.embed.external"), "external must be dropped when images present: $body")
        }

    @Test
    fun externalThumb_uploadedWhenAvailable() =
        runTest {
            val body =
                captureExternalRecordBody(
                    aLinkPreview(imageUrl = "https://cardyb.bsky.app/v1/image?url=x"),
                    thumb = EncodedImage(bytes = byteArrayOf(1, 2, 3), mimeType = "image/jpeg"),
                )
            assertTrue(body.contains("app.bsky.embed.external"), "external embed missing: $body")
            assertTrue(body.contains("thumb"), "thumb blob missing: $body")
            assertTrue(body.contains("bafblob"), "uploaded thumb blob ref missing: $body")
        }

    @Test
    fun externalThumb_failure_postsCardWithoutThumb() =
        runTest {
            // downloadThumb returns null (the fake's default) — the post still
            // succeeds and carries the card with no thumb.
            val body = captureExternalRecordBody(aLinkPreview(imageUrl = "https://cardyb.bsky.app/v1/image?url=x"))
            assertTrue(body.contains("app.bsky.embed.external"), "external embed missing: $body")
            assertFalse(body.contains("thumb"), "no thumb expected when the download yields nothing: $body")
        }

    @Test
    fun textOnly_writesNoEmbedField() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/t","cid":"baft"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.createPost(text = "no embed here", attachments = emptyList(), replyTo = null)

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(!body.contains("\"embed\""), "text-only post must omit the embed field: $body")
        }

    @Test
    fun replyWithQuote_writesBothReplyRefAndRecordEmbed() =
        runTest {
            val parentRef =
                StrongRef(
                    uri = AtUri("at://did:plc:alice/app.bsky.feed.post/parent"),
                    cid = Cid("bafparent"),
                )
            val quoteRef =
                StrongRef(
                    uri = AtUri("at://did:plc:bob/app.bsky.feed.post/quoted"),
                    cid = Cid("bafquoted"),
                )
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/rq","cid":"bafrq"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "replying and quoting",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parentRef, root = parentRef),
                    quote = quoteRef,
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            // reply (threading) and embed (content) are orthogonal — both present.
            assertTrue(body.contains("at://did:plc:alice/app.bsky.feed.post/parent"), "reply parent ref missing: $body")
            assertTrue(body.contains("app.bsky.embed.record"), "quote record embed missing: $body")
            assertTrue(body.contains("at://did:plc:bob/app.bsky.feed.post/quoted"), "quoted uri missing: $body")
        }

    @Test
    fun quotePost_emitsCreatePost_withIsQuoteTrue() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/q","cid":"bafq"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            repo.createPost(
                text = "quoting bob",
                attachments = emptyList(),
                replyTo = null,
                quote =
                    StrongRef(
                        uri = AtUri("at://did:plc:bob/app.bsky.feed.post/quoted"),
                        cid = Cid("bafquoted"),
                    ),
            )

            assertEquals(
                listOf(CreatePost(hasMedia = false, isReply = false, isQuote = true, hasExternal = false)),
                analytics.events,
            )
        }

    @Test
    fun facetExtractor_nonEmptyResult_reachesCreateRecordBody() =
        runTest {
            // Wire-through assertion: a non-empty FacetExtractor output
            // must land in the serialized Post.facets field that goes
            // out on createRecord. The FacetExtractor's own parsing /
            // resolution is exercised by DefaultFacetExtractorTest;
            // here we just prove the orchestration plumbs the result.
            val capturingExtractor =
                object : FacetExtractor {
                    override suspend fun extract(text: String) =
                        persistentListOf(
                            Facet(
                                index = FacetByteSlice(byteStart = 0, byteEnd = 6),
                                features =
                                    listOf<FacetFeaturesUnion>(
                                        FacetMention(did = Did("did:plc:fixturealice")),
                                    ),
                            ),
                        )
                }
            val capturedBody = CompletableDeferred<String>()

            val (_, repo) =
                newRepo(signedIn = true, facetExtractor = capturingExtractor) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/x","cid":"bafx"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.createPost(text = "@alice", attachments = emptyList(), replyTo = null)

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            // The facets field is on the wire with the resolved DID +
            // the byte slice. Substring checks (rather than JSON parse)
            // mirror the existing reply-mode test's strategy.
            assertTrue(body.contains("\"facets\""), "facets field missing on wire: $body")
            assertTrue(body.contains("did:plc:fixturealice"), "facet mention DID missing: $body")
            assertTrue(body.contains("\"byteStart\""), "facet byteSlice missing: $body")
        }

    @Test
    fun facetExtractor_emptyResult_omitsFacetsFieldFromRecord() =
        runTest {
            // AtField.Missing convention: an empty facets list MUST NOT
            // emit a `"facets":[]` shape on the wire. The default
            // passthrough extractor returns `persistentListOf()`, and
            // the kotlinx-serialization @EncodeDefault(NEVER) on
            // Post.facets should keep it out of the JSON entirely.
            val capturedBody = CompletableDeferred<String>()

            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/x","cid":"bafx"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.createPost(text = "no mentions or links", attachments = emptyList(), replyTo = null)

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(!body.contains("\"facets\""), "empty facets must not appear on wire: $body")
        }

    @Test
    fun ioErrorOnCreateRecord_mapsToNetworkVariant() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        throw java.io.IOException("simulated socket failure")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.createPost(text = "x", attachments = emptyList(), replyTo = null)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertTrue(
                cause is ComposerError.Network,
                "Expected ComposerError.Network, got: ${cause?.javaClass?.simpleName}",
            )
        }

    @Test
    fun noSessionAtClientResolution_mapsToUnauthorized() =
        runTest {
            // SessionState says SignedIn but XrpcClientProvider raises
            // NoSessionException — simulates the race where the session
            // is invalidated between the state check and the client
            // resolution. The mapper must surface Unauthorized, not
            // RecordCreationFailed, so the UI routes to sign-in.
            val engine = MockEngine { error("network must not be touched") }
            val xrpcClient = HttpClient(engine)
            val repo =
                DefaultPostingRepository(
                    xrpcClientProvider =
                        object : XrpcClientProvider {
                            override suspend fun authenticated(): XrpcClient = throw NoSessionException()
                        },
                    sessionStateProvider =
                        object : SessionStateProvider {
                            override val state: StateFlow<SessionState> =
                                MutableStateFlow(SessionState.SignedIn(handle = "alice.test", did = testDid))

                            override suspend fun refresh() = Unit
                        },
                    byteSource =
                        object : ImageByteSource {
                            override suspend fun read(uri: Uri): ByteArray = byteArrayOf(1)
                        },
                    encoder = passthroughEncoder(),
                    dimensionDecoder = fixedDimensions(),
                    facetExtractor = passthroughFacetExtractor(),
                    localeProvider = fixedLocaleProvider("en-US"),
                    externalLinkMetadataRepository = fakeExternalLinkMetadata(),
                    analytics = analytics,
                    dispatcher = UnconfinedTestDispatcher(),
                )

            val result = repo.createPost(text = "x", attachments = emptyList(), replyTo = null)

            xrpcClient.close()
            assertTrue(result.isFailure)
            assertEquals(ComposerError.Unauthorized, result.exceptionOrNull())
        }

    @Test
    fun signedOut_returnsUnauthorized_withoutTouchingNetwork() =
        runTest {
            val (engine, repo) =
                newRepo(signedIn = false) {
                    error("Unauthorized path must not hit the network")
                }

            val result = repo.createPost(text = "x", attachments = emptyList(), replyTo = null)

            assertTrue(result.isFailure)
            assertEquals(ComposerError.Unauthorized, result.exceptionOrNull())
            assertEquals(0, engine.requestHistory.size)
        }

    // ---------- harness ----------

    // ---- Audience gates (nubecita-33bw.3) ----
    //
    // Threadgate/postgate writes go through the same `createRecord` endpoint as
    // the post, so they're distinguished by the `collection` (and the record's
    // `$type`) in the captured request body. The post response carries rkey "abc";
    // the gate records must reuse it.

    private val postOkUri = """{"uri":"at://$testDid/app.bsky.feed.post/abc","cid":"bafx"}"""

    @Test
    fun defaultAudience_writesNoGateRecords() =
        runTest {
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(text = "hi", attachments = emptyList(), replyTo = null, audience = PostAudience.DEFAULT)

            assertTrue(result.isSuccess)
            assertEquals(1, bodies.size, "only the post record should be written")
            assertTrue(bodies.none { it.contains("threadgate") || it.contains("postgate") })
        }

    @Test
    fun nobodyAudience_writesThreadgateWithEmptyAllowAtPostRkey() =
        runTest {
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "hi",
                    attachments = emptyList(),
                    replyTo = null,
                    audience = PostAudience(ReplyAudience.Nobody, allowQuotes = true),
                )

            assertTrue(result.isSuccess)
            val threadgate = bodies.single { it.contains("app.bsky.feed.threadgate") }
            assertTrue(threadgate.contains("\"rkey\":\"abc\""), "threadgate must reuse the post rkey: $threadgate")
            assertTrue(threadgate.contains("\"allow\":[]"), "Nobody == empty allow: $threadgate")
            assertTrue(bodies.none { it.contains("postgate") }, "quotes-on must not write a postgate")
        }

    @Test
    fun combinationAudience_writesOnlyTheCheckedThreadgateRules() =
        runTest {
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "hi",
                    attachments = emptyList(),
                    replyTo = null,
                    audience =
                        PostAudience(
                            ReplyAudience.Combination(followers = true, following = false, mentioned = true),
                            allowQuotes = true,
                        ),
                )

            assertTrue(result.isSuccess)
            val threadgate = bodies.single { it.contains("app.bsky.feed.threadgate") }
            assertTrue(threadgate.contains("followerRule"), "followers checked: $threadgate")
            assertTrue(threadgate.contains("mentionRule"), "mentioned checked: $threadgate")
            assertTrue(!threadgate.contains("followingRule"), "following unchecked: $threadgate")
        }

    @Test
    fun quotesDisabled_writesPostgateDisableRuleAndNoThreadgate() =
        runTest {
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "hi",
                    attachments = emptyList(),
                    replyTo = null,
                    audience = PostAudience(ReplyAudience.Everyone, allowQuotes = false),
                )

            assertTrue(result.isSuccess)
            assertTrue(bodies.none { it.contains("threadgate") }, "Everyone must not write a threadgate")
            val postgate = bodies.single { it.contains("app.bsky.feed.postgate") }
            assertTrue(postgate.contains("\"rkey\":\"abc\""), "postgate must reuse the post rkey: $postgate")
            assertTrue(postgate.contains("disableRule"), "quotes-off == postgate disable rule: $postgate")
        }

    @Test
    fun gateWriteFailure_doesNotFailThePost() =
        runTest {
            // The threadgate write throws; the post is already live, so createPost
            // must still report success with the post URI.
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        if (request.body.toBodyString().contains("app.bsky.feed.threadgate")) {
                            throw java.io.IOException("simulated threadgate write failure")
                        }
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "hi",
                    attachments = emptyList(),
                    replyTo = null,
                    audience = PostAudience(ReplyAudience.Nobody, allowQuotes = true),
                )

            assertTrue(result.isSuccess, "a gate-write failure must not fail the post")
            assertEquals(AtUri("at://$testDid/app.bsky.feed.post/abc"), result.getOrNull())
        }

    @Test
    fun replyWithNonDefaultAudience_writesNoGates() =
        runTest {
            // Gates are top-level only — a threadgate's rkey must match the thread
            // ROOT, so a non-default audience on a reply must NOT write any gate.
            val ref =
                StrongRef(
                    uri = AtUri("at://did:plc:alice/app.bsky.feed.post/root"),
                    cid = Cid("bafroot"),
                )
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "a reply",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = ref, root = ref),
                    audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false),
                )

            assertTrue(result.isSuccess)
            assertEquals(1, bodies.size, "a reply must write only the post record")
            assertTrue(bodies.none { it.contains("threadgate") || it.contains("postgate") })
        }

    @Test
    fun nobodyAndQuotesDisabled_writesBothGatesAtPostRkey() =
        runTest {
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "lock it down",
                    attachments = emptyList(),
                    replyTo = null,
                    audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false),
                )

            assertTrue(result.isSuccess)
            assertEquals(3, bodies.size, "post + threadgate + postgate")
            val threadgate = bodies.single { it.contains("app.bsky.feed.threadgate") }
            assertTrue(threadgate.contains("\"rkey\":\"abc\"") && threadgate.contains("\"allow\":[]"))
            val postgate = bodies.single { it.contains("app.bsky.feed.postgate") }
            assertTrue(postgate.contains("\"rkey\":\"abc\"") && postgate.contains("disableRule"))
        }

    @Test
    fun postgateWriteFailure_doesNotFailThePost() =
        runTest {
            // Threadgate succeeds, postgate throws — the post (already live) must
            // still succeed, even with a partially-applied gate set.
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        if (request.body.toBodyString().contains("app.bsky.feed.postgate")) {
                            throw java.io.IOException("simulated postgate write failure")
                        }
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "lock it down",
                    attachments = emptyList(),
                    replyTo = null,
                    audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false),
                )

            assertTrue(result.isSuccess, "a postgate-write failure must not fail the post")
            assertEquals(AtUri("at://$testDid/app.bsky.feed.post/abc"), result.getOrNull())
        }

    @Test
    fun allFalseCombination_writesEmptyThreadgate() =
        runTest {
            // The picker prevents an all-false combination, but the repository is
            // defensive: it degenerates to an empty allow (== Nobody).
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        bodies.add(request.body.toBodyString())
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "hi",
                    attachments = emptyList(),
                    replyTo = null,
                    audience =
                        PostAudience(
                            ReplyAudience.Combination(followers = false, following = false, mentioned = false),
                            allowQuotes = true,
                        ),
                )

            assertTrue(result.isSuccess)
            val threadgate = bodies.single { it.contains("app.bsky.feed.threadgate") }
            assertTrue(threadgate.contains("\"allow\":[]"), "all-false combination == empty allow: $threadgate")
        }

    @Test
    fun threadgateFailure_stillAttemptsPostgate() =
        runTest {
            // Independent best-effort: a threadgate write failure must NOT prevent
            // the postgate write (Nobody + quotes-off wants both gates).
            val bodies = CopyOnWriteArrayList<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        val body = request.body.toBodyString()
                        bodies.add(body)
                        if (body.contains("app.bsky.feed.threadgate")) {
                            throw java.io.IOException("simulated threadgate write failure")
                        }
                        okJson(postOkUri)
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "lock it down",
                    attachments = emptyList(),
                    replyTo = null,
                    audience = PostAudience(ReplyAudience.Nobody, allowQuotes = false),
                )

            assertTrue(result.isSuccess)
            assertTrue(bodies.any { it.contains("app.bsky.feed.threadgate") }, "threadgate attempted")
            assertTrue(
                bodies.any { it.contains("app.bsky.feed.postgate") },
                "postgate must still be attempted despite the threadgate failure",
            )
        }

    private fun MockRequestHandleScope.okJson(json: String): HttpResponseData =
        respond(
            ByteReadChannel(json),
            HttpStatusCode.OK,
            headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    @Test
    fun encoder_replacesBytesAndMime_atUploadBlob() =
        runTest {
            // The repository MUST forward the encoder's output bytes
            // and MIME to uploadBlob, not the raw bytes from
            // ImageByteSource. This is what closes the
            // nubecita-uii gap: a 4 MB JPEG from the picker becomes a
            // 700 KB WebP at the wire, under Bluesky's 1 MB cap.
            val rawBytes = byteArrayOf(11, 22, 33, 44, 55, 66, 77, 88) // pretend "raw photo bytes"
            val encodedBytes = byteArrayOf(99, 100, 101) // pretend "compressed WebP bytes"
            val capturedEncoderInputs = mutableListOf<Pair<Int, String>>()
            val rewriteEncoder =
                object : ImageEncoder {
                    override suspend fun encodeForUpload(
                        bytes: ByteArray,
                        sourceMimeType: String,
                        maxBytes: Long,
                    ): EncodedImage {
                        capturedEncoderInputs += bytes.size to sourceMimeType
                        return EncodedImage(bytes = encodedBytes, mimeType = "image/webp")
                    }
                }

            val (engine, repo) =
                newRepo(
                    signedIn = true,
                    encoder = rewriteEncoder,
                    byteSource = canned(rawBytes),
                ) { request ->
                    when {
                        request.url.encodedPath.endsWith("uploadBlob") ->
                            okJson("""{"blob":{"ref":{"${'$'}link":"bafblob1"},"mimeType":"image/webp","size":3,"${'$'}type":"blob"}}""")
                        request.url.encodedPath.endsWith("createRecord") ->
                            okJson("""{"uri":"at://$testDid/app.bsky.feed.post/x","cid":"bafx"}""")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "compressed",
                    attachments = listOf(attachment("image/jpeg")),
                    replyTo = null,
                )

            assertTrue(result.isSuccess)
            // Encoder saw the raw byte count + the picker-derived MIME.
            assertEquals(listOf(rawBytes.size to "image/jpeg"), capturedEncoderInputs)
            // uploadBlob received the ENCODED bytes, not the raw ones.
            val uploadRequest = engine.requestHistory.first { it.url.encodedPath.endsWith("uploadBlob") }
            assertEquals(encodedBytes.toList(), uploadRequest.body.toBodyBytes().toList())
            // uploadBlob's Content-Type matches the encoder's output MIME, not the source.
            assertEquals(ContentType.parse("image/webp"), uploadRequest.body.contentType)
        }

    @Test
    fun encoder_isInvokedOncePerAttachment_evenAcrossParallelUploads() =
        runTest {
            val invocations = AtomicInteger(0)
            val countingEncoder =
                object : ImageEncoder {
                    override suspend fun encodeForUpload(
                        bytes: ByteArray,
                        sourceMimeType: String,
                        maxBytes: Long,
                    ): EncodedImage {
                        invocations.incrementAndGet()
                        return EncodedImage(bytes = bytes, mimeType = sourceMimeType)
                    }
                }

            val (_, repo) =
                newRepo(signedIn = true, encoder = countingEncoder) { request ->
                    when {
                        request.url.encodedPath.endsWith("uploadBlob") ->
                            okJson(
                                """{"blob":{"ref":{"${'$'}link":"bafblob"},"mimeType":"image/jpeg","size":3,"${'$'}type":"blob"}}""",
                            )
                        request.url.encodedPath.endsWith("createRecord") ->
                            okJson("""{"uri":"at://$testDid/app.bsky.feed.post/x","cid":"bafx"}""")
                        else -> error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "four images",
                    attachments =
                        listOf(
                            attachment("image/jpeg"),
                            attachment("image/jpeg"),
                            attachment("image/png"),
                            attachment("image/heic"),
                        ),
                    replyTo = null,
                )

            assertTrue(result.isSuccess)
            assertEquals(4, invocations.get())
        }

    @Test
    fun langs_default_setsProviderLocaleOnRecord() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(
                    signedIn = true,
                    localeProvider = fixedLocaleProvider("ja-JP"),
                ) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/lang","cid":"baflang"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            // Caller omits the langs parameter — the repo derives it
            // from the LocaleProvider.
            val result = repo.createPost(text = "hola", attachments = emptyList(), replyTo = null)

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("\"langs\":[\"ja-JP\"]"), "expected langs from provider, got: $body")
        }

    @Test
    fun langs_explicitNonNull_overridesDefault() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(
                    signedIn = true,
                    localeProvider = fixedLocaleProvider("en-US"),
                ) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/lang","cid":"baflang"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "konnichiwa",
                    attachments = emptyList(),
                    replyTo = null,
                    langs = listOf("ja-JP"),
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("\"langs\":[\"ja-JP\"]"), "explicit caller langs missing: $body")
            assertTrue(!body.contains("en-US"), "provider's default leaked through despite explicit override: $body")
        }

    @Test
    fun langs_invalidTagsDroppedSilently() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/lang","cid":"baflang"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "mixed validity",
                    attachments = emptyList(),
                    replyTo = null,
                    // The empty string and the bare "!" don't round-trip
                    // through Locale.forLanguageTag — they should be
                    // dropped, leaving only the two valid tags.
                    langs = listOf("en-US", "", "!", "es-MX"),
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("\"langs\":[\"en-US\",\"es-MX\"]"), "expected only valid tags, got: $body")
        }

    @Test
    fun langs_allInvalid_omitsLangsFieldEntirely() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/lang","cid":"baflang"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "all invalid",
                    attachments = emptyList(),
                    replyTo = null,
                    langs = listOf("", "!"),
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            // After dropping the two invalid tags, the resolved list is
            // empty — the lexicon doesn't accept `langs: []`, so the
            // field MUST be omitted entirely (AtField.Missing) rather
            // than serialized as an empty array.
            assertTrue(!body.contains("\"langs\""), "expected no langs field, got: $body")
        }

    @Test
    fun langs_explicitEmpty_omitsLangsField() =
        runTest {
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(
                    signedIn = true,
                    localeProvider = fixedLocaleProvider("en-US"),
                ) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/lang","cid":"baflang"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            // Explicit empty list ≠ null. Caller is saying "I want zero
            // langs on this post" — the repo MUST honor that and not
            // fall back to the device locale.
            val result =
                repo.createPost(
                    text = "no langs please",
                    attachments = emptyList(),
                    replyTo = null,
                    langs = emptyList(),
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(!body.contains("\"langs\""), "expected no langs field with explicit empty list, got: $body")
        }

    @Test
    fun langs_replyMode_carriesProviderDefaultAlongsideRefs() =
        runTest {
            val parentRef =
                StrongRef(
                    uri = AtUri("at://did:plc:alice/app.bsky.feed.post/parent"),
                    cid = Cid("bafparent"),
                )
            val capturedBody = CompletableDeferred<String>()
            val (_, repo) =
                newRepo(
                    signedIn = true,
                    localeProvider = fixedLocaleProvider("es-MX"),
                ) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/r","cid":"bafr"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result =
                repo.createPost(
                    text = "reply with langs",
                    attachments = emptyList(),
                    replyTo = ReplyRefs(parent = parentRef, root = parentRef),
                )

            assertTrue(result.isSuccess)
            val body = capturedBody.await()
            assertTrue(body.contains("\"langs\":[\"es-MX\"]"), "langs missing in reply mode: $body")
            assertTrue(body.contains("at://did:plc:alice/app.bsky.feed.post/parent"), "reply ref missing: $body")
        }

    @Test
    fun textOnlyPost_emitsCreatePost_withStructuralBooleansOnly() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/abc","cid":"bafyfeed"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            repo.createPost(text = "hello world", attachments = emptyList(), replyTo = null)

            assertEquals(
                listOf(CreatePost(hasMedia = false, isReply = false, isQuote = false, hasExternal = false)),
                analytics.events,
            )
        }

    @Test
    fun replyPost_emitsCreatePost_withIsReplyTrue() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/abc","cid":"bafyfeed"}""")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }
            val parentRef =
                ReplyRefs(
                    parent =
                        StrongRef(
                            uri = AtUri("at://did:plc:alice/app.bsky.feed.post/parent"),
                            cid = Cid("bafyparent"),
                        ),
                    root =
                        StrongRef(
                            uri = AtUri("at://did:plc:alice/app.bsky.feed.post/parent"),
                            cid = Cid("bafyparent"),
                        ),
                )

            repo.createPost(text = "a reply", attachments = emptyList(), replyTo = parentRef)

            assertEquals(
                listOf(CreatePost(hasMedia = false, isReply = true, isQuote = false, hasExternal = false)),
                analytics.events,
            )
        }

    @Test
    fun externalCardPost_emitsCreatePost_withHasExternalTrue() =
        runTest {
            // A link card and no images → the card lands on the post's embed,
            // so has_external is true. captureExternalRecordBody drives createPost
            // through the shared `analytics` sink.
            captureExternalRecordBody(aLinkPreview(imageUrl = null))

            assertEquals(
                listOf(CreatePost(hasMedia = false, isReply = false, isQuote = false, hasExternal = true)),
                analytics.events,
            )
        }

    @Test
    fun externalPlusImagesPost_emitsCreatePost_withHasExternalFalse() =
        runTest {
            // Images win the media slot and the card is dropped from the embed,
            // so has_external reflects what actually shipped: false (has_media true).
            // This pins the wiring to `preparedExternal != null`, not `external != null`.
            captureExternalRecordBody(
                aLinkPreview(imageUrl = null),
                attachments = listOf(attachment("image/jpeg")),
            )

            assertEquals(
                listOf(CreatePost(hasMedia = true, isReply = false, isQuote = false, hasExternal = false)),
                analytics.events,
            )
        }

    @Test
    fun failedPost_doesNotEmitCreatePost() =
        runTest {
            val (_, repo) =
                newRepo(signedIn = true) { request ->
                    if (request.url.encodedPath.endsWith("createRecord")) {
                        throw java.io.IOException("simulated socket failure")
                    } else {
                        error("Unexpected request: ${request.url}")
                    }
                }

            val result = repo.createPost(text = "x", attachments = emptyList(), replyTo = null)

            assertTrue(result.isFailure)
            assertTrue(analytics.events.isEmpty(), "create_post must not fire on a failed write")
        }

    private fun attachment(
        mime: String,
        alt: String = "",
    ): ComposerAttachment = ComposerAttachment(uri = mockk(relaxed = true), mimeType = mime, alt = alt)

    /**
     * Runs `createPost` with [attachmentCount] dummy attachments (jpeg, blank
     * alt) and returns the serialized `createRecord` request body. uploadBlob
     * is stubbed with a canned blob; the embed shape is asserted from the body.
     */
    private suspend fun captureCreatedRecordBody(
        attachmentCount: Int,
        quote: StrongRef? = null,
        dimensionDecoder: ImageDimensionDecoder = fixedDimensions(),
    ): String = captureCreatedRecordBody((0 until attachmentCount).map { attachment("image/jpeg") }, quote, dimensionDecoder)

    private fun aLinkPreview(imageUrl: String?): LinkPreview =
        LinkPreview(
            uri = "https://example.com/article",
            title = "Example title",
            description = "An example page.",
            imageUrl = imageUrl,
        )

    /**
     * Capture the createRecord body for a post carrying an [external] link card.
     * [thumb] is what the fake [ExternalLinkMetadataRepository.downloadThumb]
     * returns (`null` ⇒ no thumbnail); the `uploadBlob` stub returns a `bafblob`
     * ref so an uploaded thumb is observable in the body.
     */
    private suspend fun captureExternalRecordBody(
        external: LinkPreview,
        attachments: List<ComposerAttachment> = emptyList(),
        quote: StrongRef? = null,
        thumb: EncodedImage? = null,
    ): String {
        val capturedBody = CompletableDeferred<String>()
        val (_, repo) =
            newRepo(signedIn = true, externalLinkMetadata = fakeExternalLinkMetadata(thumb = thumb)) { request ->
                when {
                    request.url.encodedPath.endsWith("uploadBlob") ->
                        okJson(
                            """{"blob":{"ref":{"${'$'}link":"bafblob"},"mimeType":"image/jpeg","size":3,"${'$'}type":"blob"}}""",
                        )
                    request.url.encodedPath.endsWith("createRecord") -> {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/x","cid":"bafx"}""")
                    }
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        val result =
            repo.createPost(text = "post", attachments = attachments, replyTo = null, quote = quote, external = external)
        assertTrue(result.isSuccess, "createPost failed: ${result.exceptionOrNull()}")
        return capturedBody.await()
    }

    private suspend fun captureCreatedRecordBody(
        attachments: List<ComposerAttachment>,
        quote: StrongRef? = null,
        dimensionDecoder: ImageDimensionDecoder = fixedDimensions(),
    ): String {
        val capturedBody = CompletableDeferred<String>()
        val (_, repo) =
            newRepo(signedIn = true, dimensionDecoder = dimensionDecoder) { request ->
                when {
                    request.url.encodedPath.endsWith("uploadBlob") ->
                        okJson(
                            """{"blob":{"ref":{"${'$'}link":"bafblob"},"mimeType":"image/jpeg","size":3,"${'$'}type":"blob"}}""",
                        )
                    request.url.encodedPath.endsWith("createRecord") -> {
                        capturedBody.complete(request.body.toBodyString())
                        okJson("""{"uri":"at://$testDid/app.bsky.feed.post/x","cid":"bafx"}""")
                    }
                    else -> error("Unexpected request: ${request.url}")
                }
            }
        val result = repo.createPost(text = "post", attachments = attachments, replyTo = null, quote = quote)
        assertTrue(result.isSuccess, "createPost failed: ${result.exceptionOrNull()}")
        return capturedBody.await()
    }

    /** Fake [ImageDimensionDecoder] returning fixed dimensions for every input. */
    private fun fixedDimensions(
        width: Int = 1200,
        height: Int = 800,
    ): ImageDimensionDecoder =
        object : ImageDimensionDecoder {
            override fun decode(bytes: ByteArray): ImageDimensions = ImageDimensions(width = width, height = height)
        }

    private fun newRepo(
        signedIn: Boolean,
        encoder: ImageEncoder = passthroughEncoder(),
        byteSource: ImageByteSource = canned(byteArrayOf(1, 2, 3)),
        dimensionDecoder: ImageDimensionDecoder = fixedDimensions(),
        facetExtractor: FacetExtractor = passthroughFacetExtractor(),
        localeProvider: LocaleProvider = fixedLocaleProvider("en-US"),
        externalLinkMetadata: ExternalLinkMetadataRepository = fakeExternalLinkMetadata(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultPostingRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val repo =
            DefaultPostingRepository(
                xrpcClientProvider =
                    object : XrpcClientProvider {
                        override suspend fun authenticated(): XrpcClient = xrpcClient
                    },
                sessionStateProvider =
                    object : SessionStateProvider {
                        override val state: StateFlow<SessionState> =
                            MutableStateFlow(
                                if (signedIn) {
                                    SessionState.SignedIn(handle = "alice.test", did = testDid)
                                } else {
                                    SessionState.SignedOut
                                },
                            )

                        override suspend fun refresh() = Unit
                    },
                byteSource = byteSource,
                encoder = encoder,
                dimensionDecoder = dimensionDecoder,
                facetExtractor = facetExtractor,
                localeProvider = localeProvider,
                externalLinkMetadataRepository = externalLinkMetadata,
                analytics = analytics,
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }

    /**
     * Pinning a fixed locale rather than reading `Locale.getDefault()`
     * keeps these tests stable across machines and CI runners.
     */
    private fun fixedLocaleProvider(tag: String): LocaleProvider =
        object : LocaleProvider {
            override fun primaryLanguageTag(): String = tag
        }

    private fun canned(bytes: ByteArray): ImageByteSource =
        object : ImageByteSource {
            override suspend fun read(uri: Uri): ByteArray = bytes
        }

    /**
     * Pass-through [ImageEncoder]. The Bitmap-backed default impl
     * requires an Android runtime; orchestration tests just assert the
     * wiring (read -> encode -> uploadBlob) and forward whatever bytes
     * the byteSource produced. Compression behavior itself is exercised
     * by the instrumented suite that lands with nubecita-9tw.
     */
    private fun passthroughEncoder(): ImageEncoder =
        object : ImageEncoder {
            override suspend fun encodeForUpload(
                bytes: ByteArray,
                sourceMimeType: String,
                maxBytes: Long,
            ): EncodedImage = EncodedImage(bytes = bytes, mimeType = sourceMimeType)
        }

    /**
     * Fake [ExternalLinkMetadataRepository]. The repository only calls
     * [ExternalLinkMetadataRepository.downloadThumb] (the VM does the fetch), so
     * [fetch] is a stub; [downloadThumb] returns [thumb] (`null` ⇒ no thumbnail).
     */
    private fun fakeExternalLinkMetadata(thumb: EncodedImage? = null): ExternalLinkMetadataRepository =
        object : ExternalLinkMetadataRepository {
            override suspend fun fetch(url: String): LinkPreview? = null

            override suspend fun downloadThumb(imageUrl: String): EncodedImage? = thumb
        }

    /**
     * Pass-through [FacetExtractor]. Repository orchestration tests
     * assert the wiring (extract -> Post.facets) without exercising the
     * regex / handle-resolver pipeline. Facet extraction itself is
     * covered by [DefaultFacetExtractorTest].
     */
    private fun passthroughFacetExtractor(): FacetExtractor =
        object : FacetExtractor {
            override suspend fun extract(text: String) = persistentListOf<Facet>()
        }
}

private fun OutgoingContent.toBodyString(): String =
    when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
        is io.ktor.http.content.TextContent -> text
        else -> ""
    }

private fun OutgoingContent.toBodyBytes(): ByteArray =
    when (this) {
        is OutgoingContent.ByteArrayContent -> bytes()
        else -> ByteArray(0)
    }

private class RecordingAnalyticsClient : AnalyticsClient {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(property: UserProperty) = Unit

    override fun logScreen(screen: AnalyticsScreen) = Unit
}
