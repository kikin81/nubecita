package net.kikin.nubecita.core.push

import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoroutinesTest {
    @Test
    fun `returns Result_success for a normal value`() {
        val result = runCatchingExceptCancellation { 42 }

        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `wraps a non-cancellation Throwable as Result_failure`() {
        val result = runCatchingExceptCancellation<Int> { throw IllegalStateException("boom") }

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull() is IllegalStateException,
            "expected IllegalStateException to be wrapped, got ${result.exceptionOrNull()}",
        )
    }

    @Test
    fun `rethrows CancellationException rather than capturing it in Result_failure`() {
        // The whole point of this helper: plain runCatching catches Throwable,
        // including CancellationException, which converts a structured-
        // concurrency cancellation signal into a misleading "operation failed"
        // Result that the caller's retry / backoff loop will keep acting on.
        val caught =
            try {
                runCatchingExceptCancellation<Int> { throw CancellationException("cancelled") }
                null
            } catch (e: CancellationException) {
                e
            }

        assertTrue(
            caught != null,
            "CancellationException must propagate; runCatchingExceptCancellation should NOT capture it as Result.failure",
        )
        assertEquals("cancelled", caught?.message)
    }
}
