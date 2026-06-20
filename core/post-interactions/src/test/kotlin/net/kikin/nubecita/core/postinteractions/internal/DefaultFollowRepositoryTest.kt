package net.kikin.nubecita.core.postinteractions.internal

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
internal class DefaultFollowRepositoryTest {
    private val viewerDid = "did:plc:viewer123"
    private val viewerHandle = "viewer.test"
    private val subjectDid = "did:plc:subject456"

    private val createdFollowUri = AtUri("at://$viewerDid/app.bsky.graph.follow/3lkfollow")
    private val createdFollowCid = Cid("bafyreiafollow")

    @Test
    fun `follow sends createRecord with app_bsky_graph_follow collection, viewer DID, and subject DID`() =
        runTest {
            val capture = FollowRecordingEngine.respondingWith(createRecordResponseJson(createdFollowUri, createdFollowCid))
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.follow(subjectDid)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            assertEquals(createdFollowUri.raw, result.getOrThrow())

            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.createRecord", request.url.encodedPath)
            val body = jsonObjectBody(capture.bodies.single())
            assertEquals("app.bsky.graph.follow", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            val record = body["record"]!!.jsonObject
            assertEquals("app.bsky.graph.follow", record["\$type"]!!.jsonPrimitive.content)
            assertEquals(subjectDid, record["subject"]!!.jsonPrimitive.content)
            // createdAt is set to "now" — exact value isn't pinned, but the
            // field MUST be present and a non-empty ISO 8601 string.
            val createdAt = record["createdAt"]!!.jsonPrimitive.content
            assertTrue(createdAt.isNotEmpty(), "createdAt should be set")
            assertTrue(createdAt.contains('T'), "createdAt should be ISO 8601, got $createdAt")
        }

    @Test
    fun `unfollow sends deleteRecord with collection, repo and rkey parsed from the follow URI`() =
        runTest {
            val capture = FollowRecordingEngine.respondingWith(deleteRecordResponseJson())
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.unfollow(createdFollowUri.raw)

            assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()}")
            val request = capture.requests.single()
            assertEquals("/xrpc/com.atproto.repo.deleteRecord", request.url.encodedPath)
            val body = jsonObjectBody(capture.bodies.single())
            assertEquals("app.bsky.graph.follow", body["collection"]!!.jsonPrimitive.content)
            assertEquals(viewerDid, body["repo"]!!.jsonPrimitive.content)
            assertEquals("3lkfollow", body["rkey"]!!.jsonPrimitive.content)
        }

    @Test
    fun `follow surfaces 5xx server response as Result_failure`() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val repository = newRepository(engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.follow(subjectDid)

            assertTrue(result.isFailure)
        }

    @Test
    fun `follow surfaces a network exception as Result_failure`() =
        runTest {
            val engine = MockEngine { throw IOException("simulated network failure") }
            val repository = newRepository(engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.follow(subjectDid)

            assertTrue(result.isFailure)
            val cause = result.exceptionOrNull()
            assertNotNull(cause)
            assertTrue(
                cause is IOException || cause?.cause is IOException,
                "expected IOException in cause chain, got $cause",
            )
        }

    @Test
    fun `follow fails with NoSessionException when no session is signed in`() =
        runTest {
            val repository =
                DefaultFollowRepository(
                    xrpcClientProvider = FollowThrowingXrpcClientProvider { throw NoSessionException() },
                    sessionStateProvider = FollowFakeSessionStateProvider(SessionState.SignedOut),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val result = repository.follow(subjectDid)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is NoSessionException)
        }

    @Test
    fun `follow fails with NoSessionException when session state is SignedOut`() =
        runTest {
            // Belt-and-suspenders — even if the xrpcClientProvider somehow returned a
            // client (caller bug), we should still refuse to send the request because
            // there is no DID to use as the createRecord `repo`.
            val capture = FollowRecordingEngine.respondingWith(createRecordResponseJson(createdFollowUri, createdFollowCid))
            val repository =
                DefaultFollowRepository(
                    xrpcClientProvider = FollowStaticXrpcClientProvider(authenticatedClient(capture.engine)),
                    sessionStateProvider = FollowFakeSessionStateProvider(SessionState.SignedOut),
                    dispatcher = UnconfinedTestDispatcher(testScheduler),
                )

            val result = repository.follow(subjectDid)

            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is NoSessionException,
                "expected NoSessionException, got ${result.exceptionOrNull()}",
            )
            assertTrue(capture.requests.isEmpty(), "no request should be sent when there is no session")
        }

    @Test
    fun `unfollow fails with IllegalArgumentException when the AT URI is malformed`() =
        runTest {
            val capture = FollowRecordingEngine.respondingWith(deleteRecordResponseJson())
            val repository = newRepository(capture.engine, UnconfinedTestDispatcher(testScheduler))

            val result = repository.unfollow("not-an-at-uri")

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
    ): DefaultFollowRepository =
        DefaultFollowRepository(
            xrpcClientProvider = FollowStaticXrpcClientProvider(authenticatedClient(engine)),
            sessionStateProvider = FollowFakeSessionStateProvider(SessionState.SignedIn(handle = viewerHandle, did = viewerDid)),
            dispatcher = dispatcher,
        )

    private fun authenticatedClient(engine: MockEngine): XrpcClient =
        XrpcClient(
            baseUrl = "https://pds.example.test",
            httpClient = HttpClient(engine),
        )

    private fun jsonObjectBody(body: String) = Json.parseToJsonElement(body).jsonObject

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

/**
 * Captures every request the [MockEngine] receives so tests can assert URL +
 * body shape. Mirrors the recorder in DefaultLikeRepostRepositoryTest.
 */
private class FollowRecordingEngine private constructor(
    val engine: MockEngine,
    val requests: List<HttpRequestData>,
    val bodies: List<String>,
) {
    companion object {
        fun respondingWith(body: String): FollowRecordingEngine {
            val capturedRequests = mutableListOf<HttpRequestData>()
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    capturedRequests += request
                    capturedBodies += (request.body as? TextContent)?.text.orEmpty()
                    respond(
                        content = ByteReadChannel(body),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }
            return FollowRecordingEngine(
                engine = engine,
                requests = capturedRequests,
                bodies = capturedBodies,
            )
        }
    }
}

private class FollowStaticXrpcClientProvider(
    private val client: XrpcClient,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = client
}

private class FollowThrowingXrpcClientProvider(
    private val onAuthenticated: () -> Nothing,
) : XrpcClientProvider {
    override suspend fun authenticated(): XrpcClient = onAuthenticated()
}

private class FollowFakeSessionStateProvider(
    initial: SessionState,
) : SessionStateProvider {
    override val state: StateFlow<SessionState> = MutableStateFlow(initial)

    override suspend fun refresh() {
        // no-op; tests pin the session state at construction
    }
}
