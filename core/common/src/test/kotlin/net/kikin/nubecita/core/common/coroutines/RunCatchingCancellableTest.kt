package net.kikin.nubecita.core.common.coroutines

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
internal class RunCatchingCancellableTest {
    @Test
    fun `returns success for a value`() {
        assertEquals("ok", runCatchingCancellable { "ok" }.getOrNull())
    }

    @Test
    fun `captures an ordinary failure, like runCatching`() {
        val result = runCatchingCancellable { throw IOException("offline") }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `rethrows CancellationException instead of capturing it`() {
        // The whole point. `runCatching` returns a failure here; this must not.
        var threw = false
        try {
            runCatchingCancellable { throw CancellationException("navigated away") }
        } catch (expected: CancellationException) {
            threw = true
        }
        assertTrue(threw, "CancellationException must propagate, not become a Result")
    }

    @Test
    fun `bare runCatching captures cancellation — the bug this exists to prevent`() {
        // Pins the contrast, so anyone reading this understands why the helper
        // is not redundant with the stdlib.
        @Suppress("detekt:TooGenericExceptionCaught")
        val swallowed = runCatching { throw CancellationException("navigated away") }
        assertTrue(swallowed.isFailure)
        assertTrue(
            swallowed.exceptionOrNull() is CancellationException,
            "stdlib runCatching folds cancellation into a Result — that is the bug",
        )
    }

    @Test
    fun `a cancelled coroutine actually unwinds instead of continuing`() =
        runTest {
            // End-to-end rather than by construction: cancel a real coroutine
            // mid-suspend and assert the code after the call never runs.
            val started = CompletableDeferred<Unit>()
            var reachedCodeAfterCancellation = false

            val job =
                launch {
                    runCatchingCancellable {
                        started.complete(Unit)
                        CompletableDeferred<Unit>().await() // never completes
                    }
                    reachedCodeAfterCancellation = true
                }

            started.await()
            job.cancel()
            job.join()

            assertFalse(
                reachedCodeAfterCancellation,
                "swallowing cancellation would let the coroutine run on past the cancellation point",
            )
        }
}
