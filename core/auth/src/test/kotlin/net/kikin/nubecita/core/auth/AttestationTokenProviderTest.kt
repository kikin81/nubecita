package net.kikin.nubecita.core.auth

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AttestationTokenProviderTest {
    @Test
    fun `passes a fetched token through`() =
        runTest {
            assertEquals("token-1", attestationTokenProvider { "token-1" }.token())
        }

    @Test
    fun `a failing fetch yields no token instead of throwing`() =
        runTest {
            val provider = attestationTokenProvider { throw IllegalStateException("Play Integrity unavailable") }

            assertNull(provider.token())
        }

    @Test
    fun `a blank or absent token is no token`() =
        runTest {
            assertNull(attestationTokenProvider { "  " }.token())
            assertNull(attestationTokenProvider { null }.token())
        }

    @Test
    fun `cancellation is not swallowed`() =
        runTest {
            val provider = attestationTokenProvider { throw CancellationException("caller went away") }

            assertThrows<CancellationException> { provider.token() }
        }

    @Test
    fun `the no-op provider never has a token`() =
        runTest {
            assertNull(AttestationTokenProvider.None.token())
        }
}
