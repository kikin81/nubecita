package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

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
            val store = EncryptedOAuthSessionStore(ThrowingDataStore(IOException("simulated read failure")))
            assertNull(store.load())
        }

    @Test
    fun `load swallows GeneralSecurityException covering Keystore invalidation`() =
        runTest {
            // KeyPermanentlyInvalidatedException extends GeneralSecurityException; the
            // concrete subtype is an Android runtime class and can't be instantiated on
            // JVM unit tests without Robolectric, so we exercise the parent type here.
            val store =
                EncryptedOAuthSessionStore(
                    ThrowingDataStore(GeneralSecurityException("simulated key invalidation")),
                )
            assertNull(store.load())
        }

    @Test
    fun `load swallows AEADBadTagException from tampered ciphertext`() =
        runTest {
            val store =
                EncryptedOAuthSessionStore(
                    ThrowingDataStore(AEADBadTagException("simulated tamper")),
                )
            assertNull(store.load())
        }

    @Test
    fun `load swallows SerializationException from malformed plaintext JSON`() =
        runTest {
            val store =
                EncryptedOAuthSessionStore(
                    ThrowingDataStore(SerializationException("simulated decode failure")),
                )
            assertNull(store.load())
        }

    @Test(expected = IllegalStateException::class)
    fun `load propagates unexpected exception types`() =
        runTest {
            val store =
                EncryptedOAuthSessionStore(
                    ThrowingDataStore(IllegalStateException("not a storage-layer failure")),
                )
            store.load()
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

private class ThrowingDataStore(
    private val toThrow: Throwable,
) : DataStore<OAuthSession?> {
    override val data: Flow<OAuthSession?> = flow { throw toThrow }

    override suspend fun updateData(transform: suspend (t: OAuthSession?) -> OAuthSession?): OAuthSession? = throw toThrow
}
