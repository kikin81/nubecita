package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultSessionStateProviderTest {
    @Test
    fun `initial state is Loading before any refresh`() {
        val provider = DefaultSessionStateProvider(EmptySessionStore())
        assertEquals(SessionState.Loading, provider.state.value)
    }

    @Test
    fun `refresh with non-null session emits SignedIn carrying handle and did`() =
        runTest {
            val session = sampleSession(handle = "alice.bsky.social", did = "did:plc:alice")
            val provider = DefaultSessionStateProvider(SeededSessionStore(session))

            provider.refresh()

            assertEquals(
                SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice"),
                provider.state.value,
            )
        }

    @Test
    fun `refresh with null session emits SignedOut`() =
        runTest {
            val provider = DefaultSessionStateProvider(EmptySessionStore())

            provider.refresh()

            assertEquals(SessionState.SignedOut, provider.state.value)
        }

    @Test
    fun `subsequent refresh after sign-in transitions to SignedOut when store clears`() =
        runTest {
            val store = MutableSessionStore(sampleSession(handle = "alice.bsky.social"))
            val provider = DefaultSessionStateProvider(store)

            provider.refresh()
            assertEquals(
                SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:samplesubject"),
                provider.state.value,
            )

            store.clear()
            provider.refresh()
            assertEquals(SessionState.SignedOut, provider.state.value)
        }
}

private class EmptySessionStore : OAuthSessionStore {
    override suspend fun load(): OAuthSession? = null

    override suspend fun save(session: OAuthSession) = error("not under test")

    override suspend fun clear() = error("not under test")
}

private class SeededSessionStore(
    private val session: OAuthSession,
) : OAuthSessionStore {
    override suspend fun load(): OAuthSession = session

    override suspend fun save(session: OAuthSession) = error("not under test")

    override suspend fun clear() = error("not under test")
}

private class MutableSessionStore(
    initial: OAuthSession?,
) : OAuthSessionStore {
    private var current: OAuthSession? = initial

    override suspend fun load(): OAuthSession? = current

    override suspend fun save(session: OAuthSession) {
        current = session
    }

    override suspend fun clear() {
        current = null
    }
}
