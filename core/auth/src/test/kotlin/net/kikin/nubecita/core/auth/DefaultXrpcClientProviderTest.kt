package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultXrpcClientProviderTest {
    @Test
    fun `cache hit returns same instance and createClient is invoked once`() =
        runTest {
            val sessionState = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice", did = "did:plc:alice"))
            val atOAuth = mockk<AtOAuth>()
            val client = newClient()
            coEvery { atOAuth.createClient() } returns client

            val provider = newProvider(atOAuth, FakeSessionStateProvider(sessionState))

            val first = provider.authenticated()
            val second = provider.authenticated()

            assertSame(first, second)
            coVerify(exactly = 1) { atOAuth.createClient() }
        }

    @Test
    fun `cache miss after DID change creates a fresh client`() =
        runTest {
            val sessionState = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice", did = "did:plc:alice"))
            val atOAuth = mockk<AtOAuth>()
            val firstClient = newClient()
            val secondClient = newClient()
            coEvery { atOAuth.createClient() } returnsMany listOf(firstClient, secondClient)

            val provider = newProvider(atOAuth, FakeSessionStateProvider(sessionState))

            val initial = provider.authenticated()
            assertSame(firstClient, initial)

            sessionState.value = SessionState.SignedIn(handle = "bob", did = "did:plc:bob")

            val afterChange = provider.authenticated()
            assertSame(secondClient, afterChange)
            coVerify(exactly = 2) { atOAuth.createClient() }
        }

    @Test
    fun `NoSessionException when no session is persisted`() =
        runTest {
            val sessionState = MutableStateFlow<SessionState>(SessionState.SignedOut)
            val atOAuth = mockk<AtOAuth>()
            val provider = newProvider(atOAuth, FakeSessionStateProvider(sessionState))

            val thrown = runCatching { provider.authenticated() }.exceptionOrNull()
            assertTrue(thrown is NoSessionException, "expected NoSessionException, got $thrown")
            coVerify(exactly = 0) { atOAuth.createClient() }
        }

    @Test
    fun `concurrent callers from cold cache trigger exactly one createClient`() =
        runTest {
            val sessionState = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice", did = "did:plc:alice"))
            val atOAuth = mockk<AtOAuth>()
            val client = newClient()
            coEvery { atOAuth.createClient() } coAnswers {
                // Yield to let other concurrent callers reach the mutex; the
                // implementation must serialize them so only one createClient
                // runs to completion.
                kotlinx.coroutines.yield()
                client
            }

            val provider = newProvider(atOAuth, FakeSessionStateProvider(sessionState))

            val results = (1..16).map { async { provider.authenticated() } }.map { it.await() }
            advanceUntilIdle()

            assertEquals(16, results.size)
            results.forEach { assertSame(client, it) }
            coVerify(exactly = 1) { atOAuth.createClient() }
        }

    @Test
    fun `invalidate forces a fresh client on next call`() =
        runTest {
            val sessionState = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice", did = "did:plc:alice"))
            val atOAuth = mockk<AtOAuth>()
            val firstClient = newClient()
            val secondClient = newClient()
            coEvery { atOAuth.createClient() } returnsMany listOf(firstClient, secondClient)

            val provider = newProvider(atOAuth, FakeSessionStateProvider(sessionState))

            assertSame(firstClient, provider.authenticated())
            provider.invalidate()
            assertSame(secondClient, provider.authenticated())
            coVerify(exactly = 2) { atOAuth.createClient() }
        }

    @Test
    fun `session-flow transition to SignedOut invalidates the cached client`() =
        runTest {
            val sessionState = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "alice", did = "did:plc:alice"))
            val atOAuth = mockk<AtOAuth>()
            val firstClient = newClient()
            val secondClient = newClient()
            coEvery { atOAuth.createClient() } returnsMany listOf(firstClient, secondClient)

            val collectorDispatcher = StandardTestDispatcher(testScheduler)
            val collectorScope = CoroutineScope(SupervisorJob() + collectorDispatcher)
            val provider =
                DefaultXrpcClientProvider(
                    atOAuth = atOAuth,
                    sessionStateProvider = FakeSessionStateProvider(sessionState),
                    coroutineScope = collectorScope,
                )

            assertSame(firstClient, provider.authenticated())
            advanceUntilIdle()

            sessionState.value = SessionState.SignedOut
            advanceUntilIdle()

            // Re-sign in as the same DID; with the eager invalidator wired the
            // next authenticated() must trigger a fresh createClient.
            sessionState.value = SessionState.SignedIn(handle = "alice", did = "did:plc:alice")
            advanceUntilIdle()

            assertSame(secondClient, provider.authenticated())
            coVerify(exactly = 2) { atOAuth.createClient() }

            collectorScope.cancel()
        }
}

private fun newClient(): XrpcClient = XrpcClient(baseUrl = "https://example.test", httpClient = HttpClient(OkHttp))

private fun newProvider(
    atOAuth: AtOAuth,
    sessionStateProvider: SessionStateProvider,
): DefaultXrpcClientProvider {
    // Inert scope: the eager-invalidator collector launches under this scope
    // but isn't driven by the test scheduler, so its behavior doesn't bleed
    // into tests that exercise lazy invalidation. Tests that need the eager
    // path construct their own StandardTestDispatcher tied to testScheduler.
    val inertScope = CoroutineScope(SupervisorJob())
    return DefaultXrpcClientProvider(
        atOAuth = atOAuth,
        sessionStateProvider = sessionStateProvider,
        coroutineScope = inertScope,
    )
}

private class FakeSessionStateProvider(
    private val backing: MutableStateFlow<SessionState>,
) : SessionStateProvider {
    override val state: StateFlow<SessionState> = backing.asStateFlow()

    override suspend fun refresh() {
        // Tests drive `backing.value =` directly; no store to re-query here.
    }
}
