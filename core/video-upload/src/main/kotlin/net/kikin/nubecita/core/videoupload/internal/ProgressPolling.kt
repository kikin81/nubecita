package net.kikin.nubecita.core.videoupload.internal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Run [body] while polling [poll] on an interval, and stop polling the moment
 * [body] finishes — however it finishes.
 *
 * This exists because getting it wrong deadlocks. A `while (isActive)` loop
 * launched into the enclosing scope keeps that scope alive under structured
 * concurrency, so the scope waits for the loop and the loop waits for the
 * scope. On the success path the caller hangs forever. Cancelling the poll job
 * in `finally` is the whole fix, and pulling it out here makes it testable
 * without a device or an encoder.
 *
 * [Transformer][androidx.media3.transformer.Transformer] exposes progress only
 * by query, which is why polling is needed at all.
 */
internal suspend fun <T> withProgressPolling(
    pollIntervalMs: Long,
    poll: () -> Unit,
    body: suspend CoroutineScope.() -> T,
): T =
    coroutineScope {
        val pollJob =
            launch {
                while (isActive) {
                    poll()
                    delay(pollIntervalMs)
                }
            }
        try {
            body()
        } finally {
            pollJob.cancel()
        }
    }
