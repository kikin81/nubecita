package net.kikin.nubecita.feature.notifications.impl.store

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import timber.log.Timber

/**
 * Wires [NotificationsUnreadCountStore] to the app's foreground lifecycle.
 * While the process is foregrounded (lifecycle state ≥ `STARTED`), polls
 * `getUnreadCount` every 60 seconds on success, and applies exponential
 * backoff (60s → 120s → 240s → cap 300s, reset on success) on failure.
 *
 * The `repeatOnLifecycle(STARTED) { … }` pattern handles the foreground /
 * background gating natively: the inner block's coroutine is launched on
 * each `ON_START` and cancelled on each `ON_STOP`, so the `while (isActive)
 * delay()` loop pauses and resumes with the lifecycle for free — no manual
 * `onStart` / `onStop` callbacks needed.
 *
 * Logout integration: a separate collector watches
 * [SessionStateProvider.state] for a [SessionState.SignedOut] transition
 * and clears the store. Lifecycle gating isn't needed for the logout path
 * — clearing zero over and over is idempotent.
 *
 * Lifecycle dependency is injectable for tests (default
 * `ProcessLifecycleOwner.get().lifecycle`); unit coverage exercises the
 * gating via `LifecycleRegistry.createUnsafe` to avoid TestLifecycleOwner's
 * `runBlocking`-on-`setCurrentState` deadlock against a virtual
 * `TestDispatcher`.
 */
class NotificationsPollingObserver(
    private val store: NotificationsUnreadCountStore,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) {
    /**
     * Registers the polling loop against [lifecycle] and starts the
     * session-state collector. Idempotent — re-launching the polling job
     * is fine because the previous one would already be cancelled by the
     * `ON_STOP` lifecycle event (a SignedOut transition does NOT cancel
     * the polling loop; the store's `clear()` is the only side-effect of
     * sign-out, and the next foreground poll will read the post-logout
     * count of `0` regardless).
     */
    fun start() {
        scope.launch {
            // repeatOnLifecycle(STARTED) suspends until the lifecycle reaches
            // STARTED, runs the block, cancels it on STOP, and re-runs on the
            // next START. We launch ONE coroutine here on start() rather than
            // hooking onStart/onStop manually so the catch-up dispatch on
            // observer registration doesn't double-launch the poll loop.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var delayMs = INITIAL_DELAY_MS
                while (isActive) {
                    val result = store.refresh()
                    delayMs =
                        if (result.isSuccess) {
                            INITIAL_DELAY_MS
                        } else {
                            Timber.tag(TAG).w(
                                result.exceptionOrNull(),
                                "getUnreadCount failed; backing off to %dms",
                                (delayMs * BACKOFF_MULTIPLIER).coerceAtMost(MAX_DELAY_MS),
                            )
                            (delayMs * BACKOFF_MULTIPLIER).coerceAtMost(MAX_DELAY_MS)
                        }
                    delay(delayMs)
                }
            }
        }

        scope.launch {
            // StateFlow conflates identical consecutive emits natively, so the
            // explicit distinctUntilChanged operator (deprecated on StateFlow)
            // is unnecessary — a stale SignedOut re-emit can't fire store.clear()
            // twice in a row. store.clear() is itself idempotent regardless.
            sessionStateProvider.state.collect { state ->
                if (state is SessionState.SignedOut) {
                    store.clear()
                }
            }
        }
    }

    private companion object {
        const val TAG = "NotificationsPolling"

        /** 60 seconds — nominal polling cadence; also the reset target after a successful tick. */
        const val INITIAL_DELAY_MS: Long = 60_000L

        /** 5 minutes — cap on the exponential backoff after consecutive failures. */
        const val MAX_DELAY_MS: Long = 300_000L

        /** Doubles the delay on each consecutive failure: 60s → 120s → 240s → 300s (capped). */
        const val BACKOFF_MULTIPLIER: Long = 2L
    }
}
