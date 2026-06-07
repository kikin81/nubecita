package net.kikin.nubecita.feature.chats.impl.store

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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Wires [ChatsUnreadCountStore] to the app's foreground lifecycle, exactly
 * mirroring `NotificationsPollingObserver`. While the process is foregrounded
 * (lifecycle ≥ `STARTED`), refreshes the aggregate unread-DM count every 60s
 * on success and applies exponential backoff (60s → 120s → 240s → cap 300s,
 * reset on success) on failure.
 *
 * Foreground-only by construction: `repeatOnLifecycle(STARTED)` cancels the
 * loop on `ON_STOP` and re-runs on the next `ON_START`, so there is **no
 * background work** — consistent with the v1 battery rule (nubecita-1fy.14).
 * Background DM notifications are a separate effort (nubecita-1fy.15).
 *
 * Logout: a separate collector watches [SessionStateProvider.state] and
 * clears the store on [SessionState.SignedOut].
 *
 * Lifecycle is injectable for tests (default
 * `ProcessLifecycleOwner.get().lifecycle`).
 */
class ChatsUnreadPollingObserver(
    private val store: ChatsUnreadCountStore,
    private val sessionStateProvider: SessionStateProvider,
    private val scope: CoroutineScope,
    private val lifecycle: Lifecycle = ProcessLifecycleOwner.get().lifecycle,
) {
    /** Guards [start] against double-invocation (parallel loops / double network budget). */
    private val started = AtomicBoolean(false)

    /**
     * Registers the lifecycle-scoped polling loop and the session-state
     * collector. Idempotent — subsequent calls short-circuit via [started].
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
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
                                "chat unread refresh failed; backing off to %dms",
                                (delayMs * BACKOFF_MULTIPLIER).coerceAtMost(MAX_DELAY_MS),
                            )
                            (delayMs * BACKOFF_MULTIPLIER).coerceAtMost(MAX_DELAY_MS)
                        }
                    delay(delayMs)
                }
            }
        }

        scope.launch {
            sessionStateProvider.state.collect { state ->
                if (state is SessionState.SignedOut) {
                    store.clear()
                }
            }
        }
    }

    private companion object {
        const val TAG = "ChatsUnreadPolling"

        /** 60 seconds — nominal cadence; also the reset target after a successful tick. */
        const val INITIAL_DELAY_MS: Long = 60_000L

        /** 5 minutes — cap on the exponential backoff after consecutive failures. */
        const val MAX_DELAY_MS: Long = 300_000L

        /** Doubles the delay on each consecutive failure. */
        const val BACKOFF_MULTIPLIER: Long = 2L
    }
}
