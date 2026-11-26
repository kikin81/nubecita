package net.kikin.nubecita.core.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TokenMediationWiringTest {
    private val client = HttpClient(MockEngine { respond("") })

    @Test
    fun `with no mediator URL there is no transport, so the SDK goes direct and nothing is attested`() {
        var attestationCalls = 0
        val attestation = AttestationTokenProvider { null.also { attestationCalls++ } }

        val transport = tokenTransportFor(TokenMediationConfig.from(""), client, client, attestation)

        assertNull(transport)
        assertEquals(0, attestationCalls)
    }

    @Test
    fun `with a mediator URL requests go through the backend`() {
        val transport =
            tokenTransportFor(TokenMediationConfig.from("https://auth.nubecita.app"), client, client, AttestationTokenProvider.None)

        assertTrue(transport is MediatedTokenEndpointTransport)
    }
}

class SessionLifetimeProviderTest {
    private class Store(
        var session: io.github.kikin81.atproto.oauth.OAuthSession?,
    ) : io.github.kikin81.atproto.oauth.OAuthSessionStore {
        override suspend fun load() = session

        override suspend fun save(session: io.github.kikin81.atproto.oauth.OAuthSession) {
            this.session = session
        }

        override suspend fun clear() {
            session = null
        }
    }

    private fun session(clientAuthMethod: String?) =
        io.github.kikin81.atproto.oauth.OAuthSession(
            accessToken = "at",
            refreshToken = "rt",
            did = "did:plc:test",
            handle = "alice.test",
            pdsUrl = "https://pds.test",
            tokenEndpoint = "https://bsky.social/oauth/token",
            dpopPrivateKey = byteArrayOf(1),
            dpopPublicKey = byteArrayOf(2),
            clientAuthMethod = clientAuthMethod,
        )

    @Test
    fun `no session means no lifetime`() =
        runTest {
            assertNull(DefaultSessionLifetimeProvider(Store(null)).current())
        }

    @Test
    fun `a session is mediated only once the backend has reported on it`() =
        runTest {
            assertEquals(false, DefaultSessionLifetimeProvider(Store(null)).isMediated())
            assertEquals(false, DefaultSessionLifetimeProvider(Store(session(null))).isMediated())
            assertEquals(true, DefaultSessionLifetimeProvider(Store(session("none"))).isMediated())
            assertEquals(true, DefaultSessionLifetimeProvider(Store(session("private_key_jwt"))).isMediated())
        }

    @Test
    fun `a session the backend reported as confidential has the long lifetime`() =
        runTest {
            assertEquals(SessionLifetime.Confidential, DefaultSessionLifetimeProvider(Store(session("private_key_jwt"))).current())
        }

    @Test
    fun `a session reported as public, or never reported on, has the two-week lifetime`() =
        runTest {
            assertEquals(SessionLifetime.Public, DefaultSessionLifetimeProvider(Store(session("none"))).current())
            assertEquals(SessionLifetime.Public, DefaultSessionLifetimeProvider(Store(session(null))).current())
        }
}
