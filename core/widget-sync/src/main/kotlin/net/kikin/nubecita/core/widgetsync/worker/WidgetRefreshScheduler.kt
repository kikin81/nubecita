package net.kikin.nubecita.core.widgetsync.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers / cancels the background widget-refresh worker reactively (D-B2):
 * the periodic work is scheduled while the user is **signed in** and cancelled
 * on sign-out. Started once from the production-flavor `AppInitializer`
 * multibinding; the bench flavor never contributes it, so no work is ever
 * enqueued there. Mirrors `:feature:chats:impl`'s `DmPollScheduler`.
 *
 * The schedule/cancel decision lives here (JVM-unit-tested against a fake
 * [WidgetWorkScheduler]); the actual WorkManager enqueue is behind the seam.
 */
class WidgetRefreshScheduler internal constructor(
    private val scope: CoroutineScope,
    private val sessionStateProvider: SessionStateProvider,
    private val scheduler: WidgetWorkScheduler,
) {
    private val started = AtomicBoolean(false)

    /** Idempotent — subsequent calls short-circuit. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            sessionStateProvider.state
                .map { it is SessionState.SignedIn }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn) scheduler.ensureScheduled() else scheduler.cancel()
                }
        }
    }
}
