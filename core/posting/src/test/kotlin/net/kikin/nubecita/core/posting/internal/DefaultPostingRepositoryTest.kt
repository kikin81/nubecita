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
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.ComposerError
import net.kikin.nubecita.core.posting.ReplyRefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
 * [AttachmentByteSource] ignores the URI and returns canned bytes —
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
                        object : AttachmentByteSource {
                            override suspend fun read(uri: Uri): ByteArray = byteArrayOf(1)
                        },
                    encoder = passthroughEncoder(),
                    facetExtractor = passthroughFacetExtractor(),
                    localeProvider = fixedLocaleProvider("en-US"),
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
            // AttachmentByteSource. This is what closes the
            // nubecita-uii gap: a 4 MB JPEG from the picker becomes a
            // 700 KB WebP at the wire, under Bluesky's 1 MB cap.
            val rawBytes = byteArrayOf(11, 22, 33, 44, 55, 66, 77, 88) // pretend "raw photo bytes"
            val encodedBytes = byteArrayOf(99, 100, 101) // pretend "compressed WebP bytes"
            val capturedEncoderInputs = mutableListOf<Pair<Int, String>>()
            val rewriteEncoder =
                object : AttachmentEncoder {
                    override suspend fun encodeForUpload(
                        bytes: ByteArray,
                        sourceMimeType: String,
                        maxBytes: Long,
                    ): EncodedAttachment {
                        capturedEncoderInputs += bytes.size to sourceMimeType
                        return EncodedAttachment(bytes = encodedBytes, mimeType = "image/webp")
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
                object : AttachmentEncoder {
                    override suspend fun encodeForUpload(
                        bytes: ByteArray,
                        sourceMimeType: String,
                        maxBytes: Long,
                    ): EncodedAttachment {
                        invocations.incrementAndGet()
                        return EncodedAttachment(bytes = bytes, mimeType = sourceMimeType)
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

    private fun attachment(mime: String): ComposerAttachment = ComposerAttachment(uri = mockk(relaxed = true), mimeType = mime)

    private fun newRepo(
        signedIn: Boolean,
        encoder: AttachmentEncoder = passthroughEncoder(),
        byteSource: AttachmentByteSource = canned(byteArrayOf(1, 2, 3)),
        facetExtractor: FacetExtractor = passthroughFacetExtractor(),
        localeProvider: LocaleProvider = fixedLocaleProvider("en-US"),
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
                facetExtractor = facetExtractor,
                localeProvider = localeProvider,
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

    private fun canned(bytes: ByteArray): AttachmentByteSource =
        object : AttachmentByteSource {
            override suspend fun read(uri: Uri): ByteArray = bytes
        }

    /**
     * Pass-through [AttachmentEncoder]. The Bitmap-backed default impl
     * requires an Android runtime; orchestration tests just assert the
     * wiring (read -> encode -> uploadBlob) and forward whatever bytes
     * the byteSource produced. Compression behavior itself is exercised
     * by the instrumented suite that lands with nubecita-9tw.
     */
    private fun passthroughEncoder(): AttachmentEncoder =
        object : AttachmentEncoder {
            override suspend fun encodeForUpload(
                bytes: ByteArray,
                sourceMimeType: String,
                maxBytes: Long,
            ): EncodedAttachment = EncodedAttachment(bytes = bytes, mimeType = sourceMimeType)
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
