package net.kikin.nubecita.feature.chats.impl.store

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
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
    private val messageChecking: MessageCheckingPreference,
    private val scope: CoroutineScope,
    // Null in production; resolved on the main thread inside [start]. Kept out of
    // the constructor so the observer can be CONSTRUCTED off the main thread during
    // deferred startup — `ProcessLifecycleOwner.get()` is main-thread-only and was
    // blocking Application.onCreate (nubecita-jicb). Tests inject a fake directly.
    private val lifecycle: Lifecycle? = null,
) {
    /** Guards [start] against double-invocation (parallel loops / double network budget). */
    private val started = AtomicBoolean(false)

    /**
     * Registers the lifecycle-scoped polling loop and the session-state
     * collector. Idempotent — subsequent calls short-circuit via [started].
     *
     * MUST be invoked on the main thread: resolves [ProcessLifecycleOwner] when no
     * lifecycle was injected.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        val lifecycle = lifecycle ?: ProcessLifecycleOwner.get().lifecycle
        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Drive the poll loop off the toggle (design D6): collectLatest
                // cancels the running loop the moment message checking is turned
                // off, so a disabled non-chatter has zero polling — not even an
                // idle 60s tick. Re-enabling restarts the loop at the base cadence.
                messageChecking.enabled.collectLatest { enabled ->
                    if (!enabled) return@collectLatest
                    var delayMs = INITIAL_DELAY_MS
                    while (isActive) {
                        // Gate on an active session: the observer is process-scoped,
                        // so it's also foregrounded on the Login screen, at cold-start
                        // `Loading`, and after logout — where `refresh()` would hit
                        // `authenticated()` (no session), fail, and log every cycle.
                        // When signed out we skip the network call and just idle at the
                        // base cadence.
                        delayMs =
                            if (sessionStateProvider.state.value is SessionState.SignedIn) {
                                val result = store.refresh()
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
                            } else {
                                INITIAL_DELAY_MS
                            }
                        delay(delayMs)
                    }
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

        // Clear the badge immediately when message checking is turned off, so
        // "off ⇒ no unread badge" (design D6) takes effect without waiting for
        // the next poll (which is also gated off above).
        scope.launch {
            messageChecking.enabled.collect { enabled ->
                if (!enabled) store.clear()
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
