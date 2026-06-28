package net.kikin.nubecita.core.actors.internal

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DefaultMuteRepository].
 *
 * Stand up a real [XrpcClient] backed by a Ktor [MockEngine] so the SDK's
 * full `GraphService.muteActor` / `unmuteActor` codepaths run end-to-end
 * against deterministic responses. Mirrors the test harness in
 * [DefaultActorRepositoryTest].
 *
 * Coverage:
 *  - muteActor success → Result.success + correct XRPC path + actor field.
 *  - unmuteActor success → Result.success + correct XRPC path + actor field.
 *  - 4xx/network error → Result.failure.
 *  - CancellationException propagates (not swallowed into Result.failure).
 */
class DefaultMuteRepositoryTest {
    // -------------------------------------------------------------------------
    // muteActor
    // -------------------------------------------------------------------------

    @Test
    fun muteActor_success_returnsResultSuccess() =
        runTest {
            val capture = CapturingEngine.okEmpty()
            val repo = newRepo(capture.engine)

            val result = repo.muteActor("did:plc:x")

            assertTrue(result.isSuccess)
        }

    @Test
    fun muteActor_sendsCorrectXrpcPath() =
        runTest {
            val capture = CapturingEngine.okEmpty()
            val repo = newRepo(capture.engine)

            repo.muteActor("did:plc:x")

            val url =
                capture.requests
                    .single()
                    .url
                    .toString()
            assertTrue(
                url.contains("muteActor"),
                "url should contain muteActor but was: $url",
            )
        }

    @Test
    fun muteActor_sendsCorrectActorDid() =
        runTest {
            val capture = CapturingEngine.okEmpty()
            val repo = newRepo(capture.engine)

            repo.muteActor("did:plc:target123")

            val body = capture.bodies.single()
            assertTrue(
                body.contains("did:plc:target123"),
                "request body should contain the target DID but was: $body",
            )
        }

    @Test
    fun muteActor_networkFailure_returnsFailure() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.BadRequest) }
            val repo = newRepo(engine)

            val result = repo.muteActor("did:plc:x")

            assertTrue(result.isFailure)
        }

    @Test
    fun muteActor_cancellation_propagates() {
        val engine = MockEngine { throw CancellationException("scope cancelled") }
        val repo = newRepo(engine)

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.muteActor("did:plc:x") }
        }
    }

    // -------------------------------------------------------------------------
    // unmuteActor
    // -------------------------------------------------------------------------

    @Test
    fun unmuteActor_success_returnsResultSuccess() =
        runTest {
            val capture = CapturingEngine.okEmpty()
            val repo = newRepo(capture.engine)

            val result = repo.unmuteActor("did:plc:x")

            assertTrue(result.isSuccess)
        }

    @Test
    fun unmuteActor_sendsCorrectXrpcPath() =
        runTest {
            val capture = CapturingEngine.okEmpty()
            val repo = newRepo(capture.engine)

            repo.unmuteActor("did:plc:x")

            val url =
                capture.requests
                    .single()
                    .url
                    .toString()
            assertTrue(
                url.contains("unmuteActor"),
                "url should contain unmuteActor but was: $url",
            )
        }

    @Test
    fun unmuteActor_sendsCorrectActorDid() =
        runTest {
            val capture = CapturingEngine.okEmpty()
            val repo = newRepo(capture.engine)

            repo.unmuteActor("did:plc:target456")

            val body = capture.bodies.single()
            assertTrue(
                body.contains("did:plc:target456"),
                "request body should contain the target DID but was: $body",
            )
        }

    @Test
    fun unmuteActor_networkFailure_returnsFailure() =
        runTest {
            val engine = MockEngine { respondError(HttpStatusCode.BadRequest) }
            val repo = newRepo(engine)

            val result = repo.unmuteActor("did:plc:x")

            assertTrue(result.isFailure)
        }

    @Test
    fun unmuteActor_cancellation_propagates() {
        val engine = MockEngine { throw CancellationException("scope cancelled") }
        val repo = newRepo(engine)

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.unmuteActor("did:plc:x") }
        }
    }

    // -------------------------------------------------------------------------
    // Harness helpers
    // -------------------------------------------------------------------------

    private fun newRepo(engine: MockEngine): DefaultMuteRepository {
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = xrpcClient
            }
        return DefaultMuteRepository(
            xrpcClientProvider = provider,
            dispatcher = Dispatchers.Unconfined,
        )
    }
}

/**
 * Captures every request + body the [MockEngine] receives so tests can assert
 * URL and body shape. The body is materialized as a [TextContent] string inside
 * the handler where the [io.ktor.http.content.OutgoingContent] is
 * guaranteed-readable. Mirrors the RecordingEngine in DefaultLikeRepostRepositoryTest.
 */
private class CapturingEngine private constructor(
    val engine: MockEngine,
    val requests: List<io.ktor.client.request.HttpRequestData>,
    val bodies: List<String>,
) {
    companion object {
        /**
         * Creates a [CapturingEngine] that responds with an empty 200 body —
         * the AT Protocol spec for procedures that declare no `output.schema`
         * (like muteActor / unmuteActor). The SDK's XrpcClient short-circuits
         * the Unit deserializer on blank bodies.
         */
        fun okEmpty(): CapturingEngine {
            val capturedRequests = mutableListOf<io.ktor.client.request.HttpRequestData>()
            val capturedBodies = mutableListOf<String>()
            val engine =
                MockEngine { request ->
                    capturedRequests += request
                    capturedBodies += (request.body as? TextContent)?.text.orEmpty()
                    respond(
                        content = ByteReadChannel(ByteArray(0)),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
                    )
                }
            return CapturingEngine(engine = engine, requests = capturedRequests, bodies = capturedBodies)
        }
    }
}
