package net.kikin.nubecita.core.videoupload.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProgressPollingTest {
    /**
     * The regression this helper exists for.
     *
     * A `while (isActive)` poll loop launched into the enclosing scope keeps
     * that scope alive under structured concurrency: the scope waits for the
     * loop, the loop waits for the scope, and the success path hangs forever.
     * The `withTimeout` is the assertion — before the fix this test would not
     * fail, it would never return.
     */
    @Test
    fun `returns when the body completes instead of hanging on the poll loop`() =
        runTest {
            val result =
                withTimeout(5_000) {
                    withProgressPolling(pollIntervalMs = 10, poll = {}) {
                        delay(50)
                        "done"
                    }
                }

            assertEquals("done", result)
        }

    @Test
    fun `polls while the body runs`() =
        runTest {
            var polls = 0

            withProgressPolling(pollIntervalMs = 10, poll = { polls++ }) { delay(100) }

            assertTrue(polls > 1, "expected repeated polling, got $polls")
        }

    /** Once the body is done the loop must stop, not linger burning cycles. */
    @Test
    fun `stops polling after the body completes`() =
        runTest {
            var polls = 0

            withProgressPolling(pollIntervalMs = 10, poll = { polls++ }) { delay(50) }
            val atCompletion = polls
            delay(200)

            assertEquals(atCompletion, polls, "poll loop kept running after the body finished")
        }

    /** A failing body must surface its own exception, not be masked by the loop. */
    @Test
    fun `propagates a body failure`() =
        runTest {
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking {
                    withProgressPolling(pollIntervalMs = 10, poll = {}) {
                        error("body blew up")
                    }
                }
            }
        }

    /** Cancellation must still cancel — the poll job must not keep the scope alive. */
    @Test
    fun `stops polling when the body is cancelled`() =
        runTest {
            var polls = 0

            assertThrows(CancellationException::class.java) {
                kotlinx.coroutines.runBlocking {
                    withProgressPolling(pollIntervalMs = 10, poll = { polls++ }) {
                        delay(30)
                        throw CancellationException("aborted")
                    }
                }
            }

            val atCancellation = polls
            delay(150)
            assertEquals(atCancellation, polls, "poll loop outlived a cancelled body")
        }
}
