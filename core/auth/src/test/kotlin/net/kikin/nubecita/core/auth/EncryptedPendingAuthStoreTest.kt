package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.PendingAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException

class EncryptedPendingAuthStoreTest {
    @Test
    fun `save then load returns the saved pending login`() =
        runTest {
            val store = EncryptedPendingAuthStore(FakePendingDataStore())
            val pending = samplePending()
            store.save(pending)
            assertEquals(pending, store.load())
        }

    @Test
    fun `clear removes the pending login so load returns null`() =
        runTest {
            val store = EncryptedPendingAuthStore(FakePendingDataStore())
            store.save(samplePending())
            store.clear()
            assertNull(store.load())
        }

    // Pin the failure posture that differs from EncryptedOAuthSessionStore:
    // storage trouble degrades to "no pending login" — the exact behaviour we
    // had before this store existed — rather than throwing. These calls sit
    // directly in beginLogin / completeLogin, so propagating would turn a
    // recoverable sign-in into a crash on the callback path.
    @Test
    fun `load returns null instead of throwing on storage failures`() =
        runTest {
            listOf(
                IOException("disk gone"),
                AEADBadTagException("keyset rotated"),
                GeneralSecurityException("keystore unavailable"),
                SerializationException("corrupt payload"),
            ).forEach { failure ->
                val store = EncryptedPendingAuthStore(ThrowingPendingDataStore(failure))
                assertNull(store.load(), "expected null for ${failure::class.simpleName}")
            }
        }

    @Test
    fun `save and clear swallow storage failures`() =
        runTest {
            val store = EncryptedPendingAuthStore(ThrowingPendingDataStore(IOException("disk gone")))
            store.save(samplePending())
            store.clear()
        }

    @Test
    fun `a programming error still propagates`() =
        runTest {
            // The swallow list is deliberately narrow: anything that is not a
            // storage failure is a bug and must not be silently hidden.
            val store = EncryptedPendingAuthStore(ThrowingPendingDataStore(IllegalStateException("bug")))
            assertThrows<IllegalStateException> { store.load() }
        }

    private fun samplePending() =
        PendingAuth(
            state = "state-abc",
            codeVerifier = "verifier-xyz",
            redirectUri = "app.nubecita:/oauth-redirect",
            flowOrigin = "Login",
            authServerNonce = null,
            dpopPrivateKey = byteArrayOf(1, 2, 3),
            dpopPublicKey = byteArrayOf(4, 5, 6),
            issuer = "https://eurosky.social",
            authorizationEndpoint = "https://eurosky.social/oauth/authorize",
            tokenEndpoint = "https://eurosky.social/oauth/token",
            parEndpoint = "https://eurosky.social/oauth/par",
            createdAtEpochMillis = 1_000L,
        )
}

private class FakePendingDataStore : DataStore<PendingAuth?> {
    private val state = MutableStateFlow<PendingAuth?>(null)

    override val data: Flow<PendingAuth?> = state

    override suspend fun updateData(transform: suspend (t: PendingAuth?) -> PendingAuth?): PendingAuth? {
        val next = transform(state.value)
        state.update { next }
        return next
    }
}

private class ThrowingPendingDataStore(
    private val toThrow: Throwable,
) : DataStore<PendingAuth?> {
    override val data: Flow<PendingAuth?> = flow { throw toThrow }

    override suspend fun updateData(transform: suspend (t: PendingAuth?) -> PendingAuth?): PendingAuth? = throw toThrow
}
