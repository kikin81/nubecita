package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.DpopAuthProvider
import io.github.kikin81.atproto.oauth.DpopSigner
import io.github.kikin81.atproto.oauth.OAuthRefreshFailedException
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionExpiredException
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.github.kikin81.atproto.oauth.TokenEndpointRequest
import io.github.kikin81.atproto.oauth.TokenEndpointResponse
import io.github.kikin81.atproto.oauth.TokenEndpointTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.Base64

/**
 * The SDK's real refresh path driving the real transport, against a scripted
 * backend. This is where the two halves have to agree: what the transport
 * reports must lead the SDK to keep, or end, the session correctly.
 */
class MediatedRefreshIntegrationTest {
    private class Store(
        var session: OAuthSession?,
    ) : OAuthSessionStore {
        var clears = 0

        override suspend fun load() = session

        override suspend fun save(session: OAuthSession) {
            this.session = session
        }

        override suspend fun clear() {
            clears++
            session = null
        }
    }

    private class Direct : TokenEndpointTransport {
        val requests = mutableListOf<TokenEndpointRequest>()

        override suspend fun send(request: TokenEndpointRequest): TokenEndpointResponse {
            requests += request
            return TokenEndpointResponse(status = 200, body = """{"access_token":"at_direct","refresh_token":"rt_direct"}""")
        }
    }

    private class Fixture(
        val provider: DpopAuthProvider,
        val store: Store,
        val direct: Direct,
        val backendCalls: () -> Int,
    )

    private fun fixture(vararg backend: suspend MockRequestHandleScope.() -> HttpResponseData): Fixture {
        val script = backend.toMutableList()
        var calls = 0
        val signer = DpopSigner.generate()
        val keys = signer.exportKeyPair()
        val session =
            OAuthSession(
                accessToken = expiredJwt(),
                refreshToken = "rt_old",
                did = "did:plc:integration-${System.nanoTime()}",
                handle = "alice.test",
                pdsUrl = "https://pds.test",
                tokenEndpoint = "https://bsky.social/oauth/token",
                clientId = "https://nubecita.app/oauth/client-metadata.json",
                dpopPrivateKey = keys.privateKeyEncoded,
                dpopPublicKey = keys.publicKeyEncoded,
            )
        val store = Store(session)
        val direct = Direct()
        val client =
            HttpClient(
                MockEngine {
                    calls++
                    script.removeAt(0)()
                },
            ) { install(HttpTimeout) }
        val transport =
            MediatedTokenEndpointTransport(
                baseUrl = "https://auth.nubecita.app",
                mediatorClient = client,
                direct = direct,
                attestation = AttestationTokenProvider.None,
            )
        return Fixture(DpopAuthProvider(session, signer, store, transport), store, direct) { calls }
    }

    private suspend fun Fixture.refresh() = provider.onUnauthorized(mapOf("WWW-Authenticate" to """DPoP error="invalid_token""""))

    private fun expiredJwt(): String {
        val encode = { value: String -> Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray()) }
        return "${encode("""{"alg":"ES256"}""")}.${encode("""{"exp":${System.currentTimeMillis() / 1000 - 3600}}""")}.sig"
    }

    private fun MockRequestHandleScope.relayed(
        status: HttpStatusCode,
        body: String,
    ) = respond(
        body,
        status,
        headersOf(
            "Nubecita-Mediator-Origin" to listOf("upstream"),
            "Nubecita-Client-Auth-Kid" to listOf("prod-1"),
            "Nubecita-Client-Auth-Method" to listOf("private_key_jwt"),
        ),
    )

    private fun MockRequestHandleScope.backendError(
        status: HttpStatusCode,
        reason: String,
        forwarded: String,
    ) = respond(
        """{"error":"mediator_$reason","forwarded":$forwarded}""",
        status,
        headersOf("Nubecita-Mediator-Origin" to listOf("mediator")),
    )

    @Test
    fun `a backend failure during refresh leaves the stored session intact`() =
        runTest {
            val f = fixture({ backendError(HttpStatusCode.InternalServerError, "internal", "\"unknown\"") })

            assertThrows<OAuthRefreshFailedException> { f.refresh() }

            assertEquals(0, f.store.clears)
            assertEquals("rt_old", f.store.session?.refreshToken)
        }

    @Test
    fun `a backend that says invalid_grant for itself cannot sign the user out`() =
        runTest {
            val f = fixture({ respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) })

            assertThrows<OAuthRefreshFailedException> { f.refresh() }

            assertEquals(0, f.store.clears)
            assertNotNull(f.store.session)
        }

    @Test
    fun `tokens in an unmarked 200 are not adopted`() =
        runTest {
            val f = fixture({ respond("""{"access_token":"at_forged","refresh_token":"rt_forged"}""", HttpStatusCode.OK) })

            assertThrows<OAuthRefreshFailedException> { f.refresh() }

            assertEquals("rt_old", f.store.session?.refreshToken, "Tokens only come from the authorization server")
        }

    @Test
    fun `the authorization server's invalid_grant, relayed, does end the session`() =
        runTest {
            val f = fixture({ relayed(HttpStatusCode.BadRequest, """{"error":"invalid_grant"}""") })

            assertThrows<OAuthSessionExpiredException> { f.refresh() }

            assertEquals(1, f.store.clears)
        }

    @Test
    fun `a relayed success rotates the tokens and binds the session to the reported key`() =
        runTest {
            val f = fixture({ relayed(HttpStatusCode.OK, """{"access_token":"at_new","refresh_token":"rt_new"}""") })

            assertTrue(f.refresh())

            val saved = checkNotNull(f.store.session)
            assertEquals("rt_new", saved.refreshToken)
            assertEquals("prod-1", saved.clientAuthKid)
            assertEquals("private_key_jwt", saved.clientAuthMethod)
            assertFalse(saved.refreshInDoubt)
        }

    @Test
    fun `an unreachable backend falls back to the direct path on a first attempt`() =
        runTest {
            val f = fixture({ throw ConnectException("refused") })

            assertTrue(f.refresh())

            assertEquals(1, f.direct.requests.size)
            assertEquals("rt_direct", f.store.session?.refreshToken)
            assertNull(f.store.session?.clientAuthKid, "The direct path binds no key")
        }

    @Test
    fun `after a lost response, neither an unreachable backend nor bypass sends the token direct`() =
        runTest {
            val f =
                fixture(
                    { throw SocketTimeoutException("response lost") },
                    { throw ConnectException("refused") },
                    { backendError(HttpStatusCode.ServiceUnavailable, "bypass", "false") },
                    { relayed(HttpStatusCode.OK, """{"access_token":"at_new","refresh_token":"rt_new"}""") },
                )

            // The token may be spent after the first attempt...
            assertThrows<OAuthRefreshFailedException> { f.refresh() }
            assertEquals(true, f.store.session?.refreshInDoubt)
            // ...so it is not sent direct when the backend cannot be reached,
            assertThrows<OAuthRefreshFailedException> { f.refresh() }
            // ...nor when the backend says to bypass it.
            assertThrows<OAuthRefreshFailedException> { f.refresh() }
            assertTrue(f.direct.requests.isEmpty(), "A possibly-spent refresh token must never be replayed direct")
            assertEquals(0, f.store.clears)
            assertEquals(true, f.store.session?.refreshInDoubt)

            // Only the backend can answer it, and once it does the doubt is over.
            assertTrue(f.refresh())
            assertEquals("rt_new", f.store.session?.refreshToken)
            assertEquals(false, f.store.session?.refreshInDoubt)
            assertEquals(4, f.backendCalls())
        }

    @Test
    fun `bypass on a first attempt goes direct and leaves no doubt behind`() =
        runTest {
            val f = fixture({ backendError(HttpStatusCode.ServiceUnavailable, "bypass", "false") })

            assertTrue(f.refresh())

            assertEquals(1, f.direct.requests.size)
            assertEquals(false, f.store.session?.refreshInDoubt)
        }

    @Test
    fun `a refusal that forwarded nothing leaves no doubt, so a later bypass can still go direct`() =
        runTest {
            val f =
                fixture(
                    { backendError(HttpStatusCode.Conflict, "unknown_key", "false") },
                    { backendError(HttpStatusCode.ServiceUnavailable, "bypass", "false") },
                )

            assertThrows<OAuthRefreshFailedException> { f.refresh() }
            assertEquals(false, f.store.session?.refreshInDoubt, "Nothing was forwarded, so the token is not in doubt")

            assertTrue(f.refresh())
            assertEquals(1, f.direct.requests.size)
        }
}
