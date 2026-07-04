package net.kikin.nubecita.core.auth.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.GeneralSecurityException

class KeysetRecoveryTest {
    private var resets = 0
    private var reported: GeneralSecurityException? = null
    private val sleeps = mutableListOf<Long>()

    private fun recover(build: () -> String): String =
        KeysetRecovery.buildWithRecovery(
            build = build,
            reset = { resets++ },
            onRegenerated = { reported = it },
            sleep = { sleeps += it },
        )

    @Test
    fun `successful build neither sleeps nor resets nor reports`() {
        val result = recover { "handle" }

        assertEquals("handle", result)
        assertEquals(0, resets)
        assertNull(reported)
        assertTrue(sleeps.isEmpty())
    }

    @Test
    fun `transient crypto failure recovers on the delayed retry without destroying anything`() {
        // A Keystore that is briefly unavailable (e.g. just after boot) throws
        // GeneralSecurityException exactly like a corrupted keyset does. The
        // first failure must get one non-destructive delayed retry — deleting
        // the keyset on a transient error is a guaranteed logout for nothing.
        var attempts = 0

        val result =
            recover {
                attempts++
                if (attempts == 1) throw GeneralSecurityException("keystore not ready")
                "recovered"
            }

        assertEquals("recovered", result)
        assertEquals(2, attempts)
        assertEquals(0, resets, "a transient failure must never trigger the destructive regen")
        assertNull(reported)
        assertEquals(listOf(200L), sleeps, "exactly one retry, delayed by the 200ms settle policy")
    }

    @Test
    fun `persistent crypto failure resets once after the retry, reports, and rebuilds`() {
        var attempts = 0
        val first = GeneralSecurityException("still broken")
        val second = GeneralSecurityException("broken again")

        val result =
            recover {
                attempts++
                when (attempts) {
                    1 -> throw first
                    2 -> throw second
                    else -> "regenerated"
                }
            }

        assertEquals("regenerated", result)
        assertEquals(3, attempts, "transient retry + regen rebuild")
        assertEquals(1, resets)
        assertSame(second, reported)
        assertTrue(
            reported?.suppressed?.contains(first) == true,
            "the first failure must ride along as suppressed for diagnostics",
        )
        assertEquals(listOf(200L), sleeps, "exactly one delayed retry — the regen rebuild is not delayed")
    }

    @Test
    fun `the same exception instance thrown twice must not self-suppress`() {
        // Security providers can rethrow a cached exception instance;
        // Throwable.addSuppressed(itself) throws IllegalArgumentException
        // ("Self-suppression not permitted"), which would replace the real
        // failure with a bogus one on the report path.
        var attempts = 0
        val cached = GeneralSecurityException("cached instance")

        val result =
            recover {
                attempts++
                if (attempts <= 2) throw cached
                "regenerated"
            }

        assertEquals("regenerated", result)
        assertSame(cached, reported)
        assertEquals(1, resets)
    }

    @Test
    fun `default sleep preserves the thread interrupt flag`() {
        Thread.currentThread().interrupt()
        try {
            // With the flag already set, Thread.sleep throws InterruptedException
            // immediately — the wrapper must swallow it and RE-SET the flag so
            // callers relying on interruption for cancellation still see it.
            KeysetRecovery.interruptPreservingSleep(10_000)

            assertTrue(Thread.currentThread().isInterrupted, "interrupt flag must be preserved")
        } finally {
            Thread.interrupted() // clear the flag so it can't leak into other tests
        }
    }

    @Test
    fun `failure after the destructive regen propagates - the recovery is bounded`() {
        val thrown =
            runCatching {
                recover { throw GeneralSecurityException("unrecoverable keystore") }
            }.exceptionOrNull()

        assertTrue(thrown is GeneralSecurityException, "expected GeneralSecurityException, got $thrown")
        assertEquals(1, resets, "exactly one destructive attempt")
    }

    @Test
    fun `non-crypto failure propagates without retry, reset, or report`() {
        val thrown =
            runCatching {
                recover { throw IllegalStateException("not a crypto failure") }
            }.exceptionOrNull()

        assertTrue(thrown is IllegalStateException, "expected IllegalStateException, got $thrown")
        assertEquals(0, resets)
        assertTrue(sleeps.isEmpty())
    }
}
