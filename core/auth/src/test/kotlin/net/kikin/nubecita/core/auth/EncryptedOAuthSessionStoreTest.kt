package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

class EncryptedOAuthSessionStoreTest {
    @Test
    fun `save then load returns the saved session`() =
        runTest {
            val store = EncryptedOAuthSessionStore(FakeDataStore())
            val session = sampleSession()
            store.save(session)
            assertEquals(session, store.load())
        }

    @Test
    fun `clear removes persisted session so load returns null`() =
        runTest {
            val store = EncryptedOAuthSessionStore(FakeDataStore())
            store.save(sampleSession())
            store.clear()
            assertNull(store.load())
        }

    @Test
    fun `store is reusable after clear`() =
        runTest {
            val store = EncryptedOAuthSessionStore(FakeDataStore())
            store.save(sampleSession(accessToken = "first"))
            store.clear()
            val second = sampleSession(accessToken = "second")
            store.save(second)
            assertEquals(second, store.load())
        }

    @Test
    fun `load swallows IOException from underlying data store`() =
        runTest {
            val store = EncryptedOAuthSessionStore(ThrowingDataStore())
            assertNull(store.load())
        }
}

private class FakeDataStore : DataStore<OAuthSession?> {
    private val state = MutableStateFlow<OAuthSession?>(null)

    override val data: Flow<OAuthSession?> = state

    override suspend fun updateData(transform: suspend (t: OAuthSession?) -> OAuthSession?): OAuthSession? {
        val next = transform(state.value)
        state.update { next }
        return next
    }
}

private class ThrowingDataStore : DataStore<OAuthSession?> {
    override val data: Flow<OAuthSession?> = flow { throw IOException("simulated decrypt failure") }

    override suspend fun updateData(transform: suspend (t: OAuthSession?) -> OAuthSession?): OAuthSession? = throw IOException("simulated write failure")
}
