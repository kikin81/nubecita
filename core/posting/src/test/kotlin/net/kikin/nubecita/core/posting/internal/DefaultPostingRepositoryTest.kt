package net.kikin.nubecita.core.posting.internal

import android.net.Uri
import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
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

    private fun attachment(mime: String): ComposerAttachment = ComposerAttachment(uri = mockk(relaxed = true), mimeType = mime)

    private fun newRepo(
        signedIn: Boolean,
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
                byteSource =
                    object : AttachmentByteSource {
                        override suspend fun read(uri: Uri): ByteArray = byteArrayOf(1, 2, 3)
                    },
                dispatcher = UnconfinedTestDispatcher(),
            )
        return engine to repo
    }
}

private fun OutgoingContent.toBodyString(): String =
    when (this) {
        is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
        is io.ktor.http.content.TextContent -> text
        else -> ""
    }
