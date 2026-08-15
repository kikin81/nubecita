package net.kikin.nubecita.core.common.coroutines

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for suspending code: catches failures, but lets
 * [CancellationException] through untouched.
 *
 * ## Why this exists
 *
 * `kotlin.runCatching` catches [Throwable], and [CancellationException] is a
 * [Throwable]. Inside a coroutine that means a *cancelled* call — the user
 * navigated away, the ViewModel was cleared — comes back as
 * `Result.failure(CancellationException)` rather than unwinding. Three things
 * follow, and none of them are obvious at the call site:
 *
 * 1. **Cooperative cancellation stops working.** `CancellationException` is how
 *    a cancelled coroutine unwinds. Swallow it and the code after the
 *    cancellation point keeps running on a coroutine that is already dead.
 * 2. **The user is told something failed.** Callers cannot tell a cancellation
 *    from a real failure, so they paint an error for a request the user
 *    themselves ended by leaving the screen.
 * 3. **Crash diagnostics get poisoned.** Repositories log these failures at
 *    WARN, and `CrashlyticsTree` forwards every WARN as a breadcrumb. Cancelled
 *    reads then fill a small ring buffer with false-positive "failed" entries,
 *    evicting the breadcrumbs that actually precede a crash.
 *
 * Use this anywhere a `suspend` function wraps work in a `Result`. The build
 * guard `scripts/check_runcatching_cancellation.sh` fails on bare
 * `runCatching` inside a `suspend` function for exactly this reason.
 *
 * ## What it does not do
 *
 * It still catches [Throwable] otherwise, matching `runCatching`'s contract —
 * including `Error`s. If a call site needs `Error`s to propagate, catch
 * narrowly by hand instead.
 *
 * @see kotlin.runCatching
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
