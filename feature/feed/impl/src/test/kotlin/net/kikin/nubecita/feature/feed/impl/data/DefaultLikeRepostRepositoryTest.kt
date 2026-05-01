package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.com.atproto.repo.StrongRef
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultLikeRepostRepositoryTest {
    private val viewerDid = "did:plc:viewer123"
    private val viewerHandle = "viewer.test"
    private val authorDid = "did:plc:author456"

    private val postUri = AtUri("at://$authorDid/app.bsky.feed.post/3lkpost")
    private val postCid = Cid("bafyreigh2akiscaildc")
    private val postRef = StrongRef(uri = postUri, cid = postCid)

    private val createdLikeUri = AtUri("at://$viewerDid/app.bsky.feed.like/3lklike")
    private val createdLikeCid = Cid("bafyreialike")
    private val createdRepostUri = AtUri("at://$viewerDid/app.bsky.feed.repost/3lkrepost")
    private val createdRepostCid = Cid("bafyreiarepost")

    @Test
    fun `like sends createRecord with app_bsky_feed_like collection, viewer DID, and post strong ref`() =
        runTest {
            val capture = RecordingEngine.respondingWith(createRecordResponseJson(createdLikeUri, createdLikeCid))
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.like(postRef)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            assertEquals(createdLikeUri, result.getOrThrow())

            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.createRecord", request.url.encodedPath)
            val body = jsonObjectBody(request)
            assertEquals("app.bsky.feed.like", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            val record = body["record"]!!.jsonObject
            assertEquals("app.bsky.feed.like", record["\$type"]!!.jsonPrimitive.content)
            val subject = record["subject"]!!.jsonObject
            assertEquals(postUri.raw, subject["uri"]!!.jsonPrimitive.content)
            assertEquals(postCid.raw, subject["cid"]!!.jsonPrimitive.content)
            // createdAt is set to "now" — exact value isn't pinned, but the
            // field MUST be present and a non-empty RFC3339-ish string.
            val createdAt = record["createdAt"]!!.jsonPrimitive.content
            assertTrue(createdAt.isNotEmpty(), "createdAt should be set")
            assertTrue(createdAt.contains('T'), "createdAt should be ISO 8601, got $createdAt")
        }

    @Test
    fun `repost sends createRecord with app_bsky_feed_repost collection and post strong ref`() =
        runTest {
            val capture = RecordingEngine.respondingWith(createRecordResponseJson(createdRepostUri, createdRepostCid))
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.repost(postRef)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            assertEquals(createdRepostUri, result.getOrThrow())

            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.createRecord", request.url.encodedPath)
            val body = jsonObjectBody(request)
            assertEquals("app.bsky.feed.repost", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            val record = body["record"]!!.jsonObject
            assertEquals("app.bsky.feed.repost", record["\$type"]!!.jsonPrimitive.content)
            val subject = record["subject"]!!.jsonObject
            assertEquals(postUri.raw, subject["uri"]!!.jsonPrimitive.content)
            assertEquals(postCid.raw, subject["cid"]!!.jsonPrimitive.content)
        }

    @Test
    fun `unlike sends deleteRecord with collection, repo and rkey parsed from the like URI`() =
        runTest {
            val capture = RecordingEngine.respondingWith(deleteRecordResponseJson())
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.unlike(createdLikeUri)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.deleteRecord", request.url.encodedPath)
            val body = jsonObjectBody(request)
            assertEquals("app.bsky.feed.like", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            assertEquals("3lklike", body["rkey"]!!.jsonPrimitive.content)
        }

    @Test
    fun `unrepost sends deleteRecord with collection, repo and rkey parsed from the repost URI`() =
        runTest {
            val capture = RecordingEngine.respondingWith(deleteRecordResponseJson())
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.unrepost(createdRepostUri)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.deleteRecord", request.url.encodedPath)
            val body = jsonObjectBody(request)
            assertEquals("app.bsky.feed.repost", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            assertEquals("3lkrepost", body["rkey"]!!.jsonPrimitive.content)
        }

    @Test
    fun `like surfaces 5xx server response as Result_failure`() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val repository = newRepository(engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.like(postRef)

            assertTrue(result.isFailure)
        }

    @Test
    fun `like surfaces a network exception as Result_failure`() =
        runTest {
            val engine = MockEngine { throw IOException("simulated network failure") }
            val repository = newRepository(engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.like(postRef)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertNotNull(cause)
            assertTrue(
                cause is IOException || cause?.cause is IOException,
                "expected IOException in cause chain, got $cause",
            )
        }

    @Test
    fun `like fails with NoSessionException when no session is signed in`() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.OK) }
            val repository =
                DefaultLikeRepostRepository(
                    xrpcClientProvider = ThrowingXrpcClientProvider { throw NoSessionException() },
                    sessionStateProvider = FakeSessionStateProvider(SessionState.SignedOut),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val result = repository.like(postRef)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
            // The MockEngine MUST NOT be hit — we never had a session to begin with.
            // (No request capture on this engine anyway; the lack of a respond() body
            // would fail the engine if a request reached it.)
        }

    @Test
    fun `like fails with NoSessionException when session state is SignedOut`() =
        runTest {
            // Belt-and-suspenders — even if the xrpcClientProvider somehow returned a
            // client (caller bug), we should still refuse to send the request because
            // there is no DID to use as the createRecord `repo`.
            val capture = RecordingEngine.respondingWith(createRecordResponseJson(createdLikeUri, createdLikeCid))
            val repository =
                DefaultLikeRepostRepository(
                    xrpcClientProvider = StaticXrpcClientProvider(authenticatedClient(capture.engine)),
                    sessionStateProvider = FakeSessionStateProvider(SessionState.SignedOut),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val result = repository.like(postRef)

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is NoSessionException,
                "expected NoSessionException, got ${result.exceptionOrNull()}",
            )
            assertTrue(capture.requests.isEmpty(), "no request should be sent when there is no session")
        }

    @Test
    fun `unlike fails with IllegalArgumentException when the AT URI is malformed`() =
        runTest {
            val capture = RecordingEngine.respondingWith(deleteRecordResponseJson())
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.unlike(AtUri("not-an-at-uri"))

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is IllegalArgumentException,
                "expected IllegalArgumentException, got ${result.exceptionOrNull()}",
            )
            assertTrue(capture.requests.isEmpty(), "no request should be sent for a malformed AT URI")
        }

    private fun newRepository(
        engine: MockEngine,
        dispatcher: kotlinx.coroutines.test.TestDispatcher,
    ): DefaultLikeRepostRepository =
        DefaultLikeRepostRepository(
            xrpcClientProvider = StaticXrpcClientProvider(authenticatedClient(engine)),
            sessionStateProvider = FakeSessionStateProvider(SessionState.SignedIn(handle = viewerHandle, did = viewerDid)),
            dispatcher = dispatcher,
        )

    private fun authenticatedClient(engine: MockEngine): XrpcClient =
        XrpcClient(
            baseUrl = "https://pds.example.test",
            httpClient = HttpClient(engine),
        )

    private fun jsonObjectBody(request: HttpRequestData) =
        Json
            .parseToJsonElement(
                (request.body as TextContent).text,
            ).jsonObject

    private fun createRecordResponseJson(
        uri: AtUri,
        cid: Cid,
    ): String =
        """
        {"uri":"${uri.raw}","cid":"${cid.raw}","commit":null,"validationStatus":"valid"}
        """.trimIndent()

    private fun deleteRecordResponseJson(): String =
        """
        {"commit":null}
        """.trimIndent()
}

/** Captures every request the [MockEngine] receives so tests can assert URL + body shape. */
private class RecordingEngine private constructor(
    val engine: MockEngine,
    val requests: List<HttpRequestData>,
) {
    companion object {
        fun respondingWith(body: String): RecordingEngine {
            val captured = mutableListOf<HttpRequestData>()
            val engine =
                MockEngine { request ->
                    // Force the OutgoingContent to materialize as TextContent BEFORE we
                    // store the request — the body needs to be read while still in the
                    // engine's send phase, otherwise the channel may close.
                    captured += request
                    respond(
                        content = ByteReadChannel(body),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            return RecordingEngine(engine = engine, requests = captured)
        }
    }
}

private class StaticXrpcClientProvider(
    private val client: XrpcClient,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = client
}

private class ThrowingXrpcClientProvider(
    private val onAuthenticated: () -> Nothing,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = onAuthenticated()
}

private class FakeSessionStateProvider(
    initial: SessionState,
) : SessionStateProvider {
    override val state: StateFlow<SessionState> = MutableStateFlow(initial)

    override suspend fun refresh() {
        // no-op; tests pin the session state at construction
    }
}
