package net.kikin.nubecita.core.auth.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class KeysetRecoveryTest {
    @Test
    fun `successful build neither resets nor reports`() {
        var resets = 0
        var reported: GeneralSecurityException? = null

        val result =
            KeysetRecovery.buildWithRecovery(
                build = { "handle" },
                reset = { resets++ },
                onRegenerated = { reported = it },
            )

        assertEquals("handle", result)
        assertEquals(0, resets)
        assertNull(reported)
    }

    @Test
    fun `crypto failure resets once, reports the cause, and retries the build`() {
        var attempts = 0
        var resets = 0
        var reported: GeneralSecurityException? = null
        val failure = GeneralSecurityException("corrupted keyset")

        val result =
            KeysetRecovery.buildWithRecovery(
                build = {
                    attempts++
                    if (attempts == 1) throw failure
                    "regenerated"
                },
                reset = { resets++ },
                onRegenerated = { reported = it },
            )

        assertEquals("regenerated", result)
        assertEquals(2, attempts)
        assertEquals(1, resets)
        assertSame(failure, reported)
    }

    @Test
    fun `second consecutive crypto failure propagates - the retry is bounded`() {
        val thrown =
            runCatching {
                KeysetRecovery.buildWithRecovery<String>(
                    build = { throw GeneralSecurityException("still broken") },
                    reset = {},
                    onRegenerated = {},
                )
            }.exceptionOrNull()

        assertTrue(thrown is GeneralSecurityException, "expected GeneralSecurityException, got $thrown")
    }

    @Test
    fun `non-crypto failure propagates without reset or report`() {
        var resets = 0

        val thrown =
            runCatching {
                KeysetRecovery.buildWithRecovery<String>(
                    build = { throw IllegalStateException("not a crypto failure") },
                    reset = { resets++ },
                    onRegenerated = {},
                )
            }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException, "expected IllegalStateException, got $thrown")
        assertEquals(0, resets)
    }
}
