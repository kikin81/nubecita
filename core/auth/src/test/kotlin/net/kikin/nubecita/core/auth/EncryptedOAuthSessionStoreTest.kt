package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class EncryptedOAuthSessionStoreTest {
    private val telemetry = mockk<SessionTelemetry>(relaxed = true)

    private fun store(dataStore: DataStore<OAuthSession?>) = EncryptedOAuthSessionStore(dataStore, telemetry)

    @Test
    fun `save then load returns the saved session`() =
        runTest {
            val store = store(FakeDataStore())
            val session = sampleSession()
            store.save(session)
            assertEquals(session, store.load())
        }

    @Test
    fun `clear removes persisted session so load returns null`() =
        runTest {
            val store = store(FakeDataStore())
            store.save(sampleSession())
            store.clear()
            assertNull(store.load())
        }

    @Test
    fun `store is reusable after clear`() =
        runTest {
            val store = store(FakeDataStore())
            store.save(sampleSession(accessToken = "first"))
            store.clear()
            val second = sampleSession(accessToken = "second")
            store.save(second)
            assertEquals(second, store.load())
        }

    @Test
    fun `clear records session-cleared telemetry after the wipe succeeds`() =
        runTest {
            val store = store(FakeDataStore())
            store.save(sampleSession())

            store.clear()

            coVerify(exactly = 1) { telemetry.onSessionCleared(any()) }
        }

    @Test
    fun `clear that fails to write records no telemetry`() =
        runTest {
            val store = store(ThrowingDataStore(IOException("simulated write failure")))

            runCatching { store.clear() }

            coVerify(exactly = 0) { telemetry.onSessionCleared(any()) }
        }

    @Test
    fun `successful load records no telemetry`() =
        runTest {
            val store = store(FakeDataStore())
            store.save(sampleSession())
            store.load()
            assertNull(store(FakeDataStore()).load())

            verify(exactly = 0) { telemetry.onSessionReadError(any()) }
        }

    @Test
    fun `loadResult distinguishes Loaded from Absent`() =
        runTest {
            val store = store(FakeDataStore())
            assertEquals(SessionLoadResult.Absent, store.loadResult())

            val session = sampleSession()
            store.save(session)
            assertEquals(SessionLoadResult.Loaded(session), store.loadResult())
        }

    @Test
    fun `loadResult surfaces a read failure as ReadError with the original cause, never Absent`() =
        runTest {
            val cause = GeneralSecurityException("keystore transiently unavailable")
            val store = store(ThrowingDataStore(cause))

            val result = store.loadResult()

            assertEquals(SessionLoadResult.ReadError(cause), result)
            verify(exactly = 1) { telemetry.onSessionReadError(cause) }
        }

    @Test
    fun `loadResult surfaces IOException and SerializationException as ReadError`() =
        runTest {
            val io = IOException("disk contention")
            assertEquals(SessionLoadResult.ReadError(io), store(ThrowingDataStore(io)).loadResult())

            val ser = SerializationException("corrupt payload")
            assertEquals(SessionLoadResult.ReadError(ser), store(ThrowingDataStore(ser)).loadResult())
        }

    @Test
    fun `loadResult rethrows cancellation - never a ReadError, never telemetry`() =
        runTest {
            val store = store(ThrowingDataStore(kotlinx.coroutines.CancellationException("scope cancelled")))

            val thrown = runCatching { store.loadResult() }.exceptionOrNull()

            assertTrue(
                thrown is kotlinx.coroutines.CancellationException,
                "expected CancellationException, got $thrown",
            )
            verify(exactly = 0) { telemetry.onSessionReadError(any()) }
        }

    @Test
    fun `loadResult propagates unexpected exception types`() =
        runTest {
            val store = store(ThrowingDataStore(IllegalStateException("not a storage-layer failure")))

            val thrown = runCatching { store.loadResult() }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException, "expected IllegalStateException, got $thrown")
        }

    @Test
    fun `load swallows IOException from underlying data store`() =
        runTest {
            val cause = IOException("simulated read failure")
            val store = store(ThrowingDataStore(cause))

            assertNull(store.load())

            verify(exactly = 1) { telemetry.onSessionReadError(cause) }
        }

    @Test
    fun `load swallows GeneralSecurityException covering Keystore invalidation`() =
        runTest {
            // KeyPermanentlyInvalidatedException extends GeneralSecurityException; the
            // concrete subtype is an Android runtime class and can't be instantiated on
            // JVM unit tests without Robolectric, so we exercise the parent type here.
            val cause = GeneralSecurityException("simulated key invalidation")
            val store = store(ThrowingDataStore(cause))

            assertNull(store.load())

            verify(exactly = 1) { telemetry.onSessionReadError(cause) }
        }

    @Test
    fun `load swallows AEADBadTagException from tampered ciphertext`() =
        runTest {
            val cause = AEADBadTagException("simulated tamper")
            val store = store(ThrowingDataStore(cause))

            assertNull(store.load())

            verify(exactly = 1) { telemetry.onSessionReadError(cause) }
        }

    @Test
    fun `load swallows SerializationException from malformed plaintext JSON`() =
        runTest {
            val cause = SerializationException("simulated decode failure")
            val store = store(ThrowingDataStore(cause))

            assertNull(store.load())

            verify(exactly = 1) { telemetry.onSessionReadError(cause) }
        }

    @Test
    fun `load propagates unexpected exception types without telemetry`() =
        runTest {
            val store = store(ThrowingDataStore(IllegalStateException("not a storage-layer failure")))

            val thrown = runCatching { store.load() }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException, "expected IllegalStateException, got $thrown")
            verify(exactly = 0) { telemetry.onSessionReadError(any()) }
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
