package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.TokenEndpointRequest
import io.github.kikin81.atproto.oauth.TokenEndpointRequestKind
import io.github.kikin81.atproto.oauth.TokenEndpointResponse
import io.github.kikin81.atproto.oauth.TokenEndpointResponseOrigin
import io.github.kikin81.atproto.oauth.TokenEndpointTransport
import io.github.kikin81.atproto.oauth.TokenRequestNotSentException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.TokenRequestPath
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MediatedTokenEndpointTransportTest {
    private class Seen(
        val url: String,
        val headers: Map<String, String>,
        val form: Map<String, String>,
    )

    private class FakeDirect(
        private val answer: suspend (TokenEndpointRequest) -> TokenEndpointResponse = {
            TokenEndpointResponse(status = 200, body = """{"access_token":"direct"}""")
        },
    ) : TokenEndpointTransport {
        val requests = mutableListOf<TokenEndpointRequest>()
        val paths = mutableListOf<TokenRequestPath?>()

        override suspend fun send(request: TokenEndpointRequest): TokenEndpointResponse {
            requests += request
            paths += currentCoroutineContext()[TokenPathElement]?.path
            return answer(request)
        }
    }

    private val seen = mutableListOf<Seen>()

    // Recorded where the real telemetry reads it: in a client plugin, which
    // runs in the caller's coroutine. (The engine runs requests in its own.)
    private val pathsSeenByPlugins = mutableListOf<TokenRequestPath?>()

    private fun transport(
        direct: TokenEndpointTransport = FakeDirect(),
        attestation: AttestationTokenProvider = AttestationTokenProvider { "appcheck-token" },
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): MediatedTokenEndpointTransport {
        val client =
            HttpClient(
                MockEngine { request ->
                    seen +=
                        Seen(
                            url = request.url.toString(),
                            headers = request.headers.entries().associate { (name, values) -> name to values.joinToString(",") },
                            form =
                                parseQueryString(request.body.toByteArray().decodeToString())
                                    .entries()
                                    .associate { (name, values) -> name to values.last() },
                        )
                    handler(request)
                },
            ) {
                install(HttpTimeout)
                install(
                    createClientPlugin("RecordTokenPath") {
                        on(Send) { request ->
                            pathsSeenByPlugins += currentCoroutineContext()[TokenPathElement]?.path
                            proceed(request)
                        }
                    },
                )
            }
        return MediatedTokenEndpointTransport(
            baseUrl = "https://auth.nubecita.app",
            mediatorClient = client,
            direct = direct,
            attestation = attestation,
        )
    }

    private fun request(
        kind: TokenEndpointRequestKind = TokenEndpointRequestKind.Refresh,
        clientAuthKid: String? = null,
        outcomeInDoubt: Boolean = false,
    ) = TokenEndpointRequest(
        kind = kind,
        endpointUrl = "https://bsky.social/oauth/token",
        issuer = "https://bsky.social",
        formParameters = mapOf("grant_type" to "refresh_token", "refresh_token" to "ref-1", "client_id" to "https://nubecita.app/c.json"),
        dpopProof = "proof.jwt.sig",
        clientAuthKid = clientAuthKid,
        outcomeInDoubt = outcomeInDoubt,
    )

    private fun MockRequestHandleScope.relayed(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"access_token":"at","refresh_token":"ref-2"}""",
        extra: Map<String, String> = emptyMap(),
    ) = respond(
        body,
        status,
        headersOf(
            *(
                mapOf(
                    "Nubecita-Mediator-Origin" to "upstream",
                    "Nubecita-Client-Auth-Kid" to "prod-1",
                    "Nubecita-Client-Auth-Method" to "private_key_jwt",
                    "Nubecita-Upstream-Date" to "Wed, 25 Nov 2026 10:00:00 GMT",
                    "DPoP-Nonce" to "nonce-2",
                ) + extra
            ).map { (name, value) -> name to listOf(value) }.toTypedArray(),
        ),
    )

    private fun MockRequestHandleScope.mediatorError(
        status: HttpStatusCode,
        reason: String,
        forwarded: String,
    ) = respond(
        """{"error":"mediator_$reason","forwarded":$forwarded}""",
        status,
        headersOf("Nubecita-Mediator-Origin" to listOf("mediator")),
    )

    // --- What is sent ---

    @Test
    fun `each kind of request goes to its path on the backend`() =
        runTest {
            val transport = transport { relayed() }

            for (kind in TokenEndpointRequestKind.entries) transport.send(request(kind))

            assertEquals(
                listOf(
                    "https://auth.nubecita.app/par",
                    "https://auth.nubecita.app/token",
                    "https://auth.nubecita.app/token",
                    "https://auth.nubecita.app/revoke",
                ),
                seen.map { it.url },
            )
        }

    @Test
    fun `the form and DPoP proof go on unchanged with the issuer, key id and attestation token`() =
        runTest {
            transport { relayed() }.send(request(clientAuthKid = "prod-1"))

            val sent = seen.single()
            assertEquals(mapOf("grant_type" to "refresh_token", "refresh_token" to "ref-1", "client_id" to "https://nubecita.app/c.json"), sent.form)
            assertEquals("proof.jwt.sig", sent.headers["DPoP"])
            assertEquals("https://bsky.social", sent.headers["Nubecita-Issuer"])
            assertEquals("prod-1", sent.headers["Nubecita-Client-Auth-Kid"])
            assertEquals("appcheck-token", sent.headers["X-Firebase-AppCheck"])
            assertEquals(listOf<TokenRequestPath?>(TokenRequestPath.Mediated), pathsSeenByPlugins)
        }

    @Test
    fun `no key id and no attestation token means neither header is sent`() =
        runTest {
            transport(attestation = AttestationTokenProvider.None) { relayed() }.send(request())

            val sent = seen.single()
            assertFalse(sent.headers.containsKey("Nubecita-Client-Auth-Kid"))
            assertFalse(sent.headers.containsKey("X-Firebase-AppCheck"))
        }

    @Test
    fun `it asks the SDK to track refresh outcomes`() {
        assertTrue(transport { relayed() }.tracksRefreshOutcome)
    }

    // --- What comes back ---

    @Test
    fun `a relayed answer is the authorization server's, with everything the backend reported`() =
        runTest {
            val response = transport { relayed(extra = mapOf("Nubecita-Replayed" to "1")) }.send(request())

            assertEquals(200, response.status)
            assertEquals("""{"access_token":"at","refresh_token":"ref-2"}""", response.body)
            assertEquals(TokenEndpointResponseOrigin.AuthorizationServer, response.origin)
            assertEquals("prod-1", response.clientAuthKid)
            assertEquals("private_key_jwt", response.clientAuthMethod)
            assertEquals("nonce-2", response.dpopNonce)
            assertEquals("Wed, 25 Nov 2026 10:00:00 GMT", response.serverDate)
            assertTrue(response.replayed)
        }

    @Test
    fun `a relayed invalid_grant stays the authorization server's`() =
        runTest {
            val response = transport { relayed(HttpStatusCode.BadRequest, """{"error":"invalid_grant"}""") }.send(request())

            assertEquals(400, response.status)
            assertEquals(TokenEndpointResponseOrigin.AuthorizationServer, response.origin)
        }

    @Test
    fun `an answer without the relay mark is the backend's, even if it says invalid_grant`() =
        runTest {
            val response = transport { respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }.send(request())

            assertEquals(TokenEndpointResponseOrigin.Mediator, response.origin)
            assertNull(response.clientAuthKid)
        }

    @Test
    fun `an unmarked 200 carrying tokens is still the backend's, never the authorization server's`() =
        runTest {
            val response =
                transport { respond("""{"access_token":"at_forged","refresh_token":"rt_forged"}""", HttpStatusCode.OK) }.send(request())

            assertEquals(200, response.status)
            assertEquals(TokenEndpointResponseOrigin.Mediator, response.origin)
        }

    @Test
    fun `a backend error that may have been forwarded comes back as the backend's`() =
        runTest {
            val response =
                transport { mediatorError(HttpStatusCode.GatewayTimeout, "upstream_timeout", "\"unknown\"") }.send(request())

            assertEquals(504, response.status)
            assertEquals(TokenEndpointResponseOrigin.Mediator, response.origin)
        }

    // --- Provably not forwarded ---

    @Test
    fun `a backend error that says nothing was forwarded is reported as not sent`() =
        runTest {
            val direct = FakeDirect()
            val transport = transport(direct) { mediatorError(HttpStatusCode.Conflict, "unknown_key", "false") }

            assertThrows<TokenRequestNotSentException> { transport.send(request()) }
            assertTrue(direct.requests.isEmpty(), "Only bypass and an unreachable backend send a request direct")
        }

    @Test
    fun `bypass sends the same request direct`() =
        runTest {
            val direct = FakeDirect()
            val transport = transport(direct) { mediatorError(HttpStatusCode.ServiceUnavailable, "bypass", "false") }
            val original = request(clientAuthKid = "prod-1")

            val response = transport.send(original)

            assertEquals("""{"access_token":"direct"}""", response.body)
            assertEquals(listOf(original), direct.requests)
            assertEquals(listOf<TokenRequestPath?>(TokenRequestPath.DirectFallback), direct.paths)
        }

    @Test
    fun `an unreachable backend sends the same request direct`() =
        runTest {
            for (failure in listOf(ConnectException("refused"), UnknownHostException("auth.nubecita.app"))) {
                val direct = FakeDirect()

                val response = transport(direct) { throw failure }.send(request())

                assertEquals("""{"access_token":"direct"}""", response.body, failure.toString())
                assertEquals(1, direct.requests.size, failure.toString())
            }
        }

    @Test
    fun `a failed direct fallback is just the direct answer`() =
        runTest {
            val direct = FakeDirect { TokenEndpointResponse(status = 400, body = """{"error":"invalid_request"}""") }

            val response = transport(direct) { throw ConnectException("refused") }.send(request())

            assertEquals(400, response.status)
            assertEquals(TokenEndpointResponseOrigin.AuthorizationServer, response.origin)
        }

    // --- Never direct when the token may be spent ---

    @Test
    fun `an in-doubt token is never sent direct when the backend is unreachable`() =
        runTest {
            val direct = FakeDirect()
            val transport = transport(direct) { throw ConnectException("refused") }

            assertThrows<TokenRequestNotSentException> { transport.send(request(outcomeInDoubt = true)) }
            assertTrue(direct.requests.isEmpty())
        }

    @Test
    fun `an in-doubt token is never sent direct on bypass`() =
        runTest {
            val direct = FakeDirect()
            val transport = transport(direct) { mediatorError(HttpStatusCode.ServiceUnavailable, "bypass", "false") }

            assertThrows<TokenRequestNotSentException> { transport.send(request(outcomeInDoubt = true)) }
            assertTrue(direct.requests.isEmpty())
        }

    @Test
    fun `a timeout after sending is not retried direct and is not reported as not sent`() =
        runTest {
            val direct = FakeDirect()
            val transport = transport(direct) { throw SocketTimeoutException("read timed out") }

            assertThrows<SocketTimeoutException> { transport.send(request()) }
            assertTrue(direct.requests.isEmpty())
        }

    @Test
    fun `a connect failure that followed an earlier attempt is not treated as not sent`() =
        runTest {
            // OkHttp keeps a re-sent request's first failure as suppressed: the
            // first POST may have reached the backend.
            val failure = ConnectException("refused").apply { addSuppressed(SocketTimeoutException("first attempt")) }
            val direct = FakeDirect()

            assertThrows<ConnectException> { transport(direct) { throw failure }.send(request()) }
            assertTrue(direct.requests.isEmpty())
        }

    // --- Attestation ---

    @Test
    fun `a sign-in refused for attestation is a device-verification failure`() =
        runTest {
            for (kind in listOf(TokenEndpointRequestKind.PushedAuthorization, TokenEndpointRequestKind.CodeExchange)) {
                val direct = FakeDirect()
                val transport = transport(direct) { mediatorError(HttpStatusCode.Forbidden, "attestation_required", "false") }

                assertThrows<DeviceVerificationRequiredException> { transport.send(request(kind)) }
                assertTrue(direct.requests.isEmpty(), "A refused sign-in must not be retried around the backend")
            }
        }

    @Test
    fun `a refresh refused for attestation is an ordinary not-sent failure, never a sign-out`() =
        runTest {
            val transport = transport { mediatorError(HttpStatusCode.Forbidden, "attestation_required", "false") }

            assertThrows<TokenRequestNotSentException> { transport.send(request(TokenEndpointRequestKind.Refresh)) }
        }

    @Test
    fun `sign-in and refresh are both sent when no attestation token can be had`() =
        runTest {
            val transport = transport(attestation = AttestationTokenProvider.None) { relayed() }

            transport.send(request(TokenEndpointRequestKind.PushedAuthorization))
            transport.send(request(TokenEndpointRequestKind.Refresh))

            assertEquals(2, seen.size)
        }
}
