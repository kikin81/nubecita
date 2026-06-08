package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers / cancels the background DM-poll worker reactively (design D6/D8):
 * the work is scheduled only while the user is **signed in** AND the
 * message-checking toggle is **on**, and cancelled otherwise (opt-out or
 * logout). Started once from the production-flavor `AppInitializer`
 * multibinding; the bench flavor never contributes it, so no work is ever
 * enqueued there.
 *
 * The schedule/cancel decision lives here (JVM-unit-tested against a fake
 * [DmWorkScheduler]); the actual WorkManager enqueue is behind the seam.
 */
class DmPollScheduler internal constructor(
    private val scope: CoroutineScope,
    private val sessionStateProvider: SessionStateProvider,
    private val messageChecking: MessageCheckingPreference,
    private val scheduler: DmWorkScheduler,
) {
    private val started = AtomicBoolean(false)

    /** Idempotent — subsequent calls short-circuit. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(
                sessionStateProvider.state,
                messageChecking.enabled,
            ) { session, enabled -> session is SessionState.SignedIn && enabled }
                .distinctUntilChanged()
                .collect { shouldSchedule ->
                    if (shouldSchedule) scheduler.ensureScheduled() else scheduler.cancel()
                }
        }
    }
}
