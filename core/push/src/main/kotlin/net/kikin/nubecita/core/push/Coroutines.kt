package net.kikin.nubecita.core.push

import kotlinx.coroutines.CancellationException

/**
 * Like [runCatching] but lets [CancellationException] propagate so the
 * surrounding structured-concurrency contract isn't violated.
 *
 * Use whenever wrapping a suspend call's outcome into [Result] inside code
 * that runs under a coroutine the caller may cancel. Plain `runCatching`
 * catches `Throwable`, including `CancellationException`, and converts the
 * cancellation signal into a `Result.failure(CancellationException)` that
 * the caller misinterprets as "the operation failed and might be retried"
 * — the surrounding loop or backoff path then keeps doing work after the
 * coroutine has been requested to stop.
 */
internal inline fun <T> runCatchingExceptCancellation(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
