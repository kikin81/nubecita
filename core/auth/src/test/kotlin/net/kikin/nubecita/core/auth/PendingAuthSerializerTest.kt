package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.oauth.PendingAuth
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The JSON layer the encrypted pending-auth store round-trips through.
 *
 * [EncryptedPendingAuthStoreTest] exercises the store against a fake
 * `DataStore` that hands the same instance back, so it never serializes
 * anything — this is where the actual write → read survives, which is the
 * mechanism the whole process-death fix depends on.
 */
class PendingAuthSerializerTest {
    @Test
    fun `round-trip preserves every field including the DPoP keypair`() =
        runTest {
            // The keypair is the reason this test exists: PendingAuth carries it
            // as ByteArrays, and a signer rehydrated from mangled bytes would
            // fail the token exchange rather than fail loudly here.
            val original = samplePending()
            val out = ByteArrayOutputStream()

            PendingAuthSerializer.writeTo(original, out)
            val restored = PendingAuthSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))

            assertEquals(original, restored)
            assertArrayEquals(original.dpopPrivateKey, restored?.dpopPrivateKey)
            assertArrayEquals(original.dpopPublicKey, restored?.dpopPublicKey)
        }

    @Test
    fun `an empty stream reads as no pending login`() =
        runTest {
            // DataStore hands us an empty stream for a file that exists but has
            // never been written — the default state, not an error.
            assertNull(PendingAuthSerializer.readFrom(ByteArrayInputStream(ByteArray(0))))
        }

    @Test
    fun `a malformed payload reads as no pending login instead of throwing`() =
        runTest {
            // A truncated or otherwise unreadable record is indistinguishable
            // from having none, and both mean "start sign-in again". Throwing
            // here would surface on the callback path.
            val garbage = """{"state":"abc","codeVerifier":""".toByteArray()
            assertNull(PendingAuthSerializer.readFrom(ByteArrayInputStream(garbage)))
        }

    @Test
    fun `writing null emits nothing`() =
        runTest {
            val out = ByteArrayOutputStream()
            PendingAuthSerializer.writeTo(null, out)
            assertEquals(0, out.size())
        }

    @Test
    fun `a record written with unknown future fields still reads back`() =
        runTest {
            // Forward compatibility: an app downgrade can meet a record written
            // by a newer build mid-Custom-Tab roundtrip. Unknown keys must be
            // ignored rather than nulling out an otherwise valid login.
            val out = ByteArrayOutputStream()
            PendingAuthSerializer.writeTo(samplePending(), out)
            val withExtra = out.toString(Charsets.UTF_8).replaceFirst("{", """{"someFutureField":"x",""")

            val restored = PendingAuthSerializer.readFrom(ByteArrayInputStream(withExtra.toByteArray()))

            assertEquals("state-abc", restored?.state)
            assertArrayEquals(byteArrayOf(1, 2, 3), restored?.dpopPrivateKey)
        }

    private fun samplePending() =
        PendingAuth(
            state = "state-abc",
            codeVerifier = "verifier-xyz",
            redirectUri = "https://nubecita.app/oauth-redirect/",
            flowOrigin = "Login",
            authServerNonce = "nonce-1",
            dpopPrivateKey = byteArrayOf(1, 2, 3),
            dpopPublicKey = byteArrayOf(4, 5, 6),
            issuer = "https://eurosky.social",
            authorizationEndpoint = "https://eurosky.social/oauth/authorize",
            tokenEndpoint = "https://eurosky.social/oauth/token",
            parEndpoint = "https://eurosky.social/oauth/par",
            revocationEndpoint = "https://eurosky.social/oauth/revoke",
            pdsUrl = "https://eurosky.social",
            did = "did:plc:testuser",
            handle = "kikin81.eurosky.social",
            promptValuesSupported = listOf("login", "create"),
            createdAtEpochMillis = 1_700_000_000_000L,
        )
}
