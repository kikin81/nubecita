package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Registers / cancels the background DM-poll worker reactively (design D6/D8):
 * the work is scheduled while the user is **signed in** AND the message-checking
 * toggle is **on**, cancelled on a definitive opt-out or **sign-out**, and left
 * **untouched** while the session is still [SessionState.Loading].
 *
 * The Loading no-op matters: [SessionStateProvider] emits `Loading` before it
 * resolves the persisted session on every launch. Treating that transient as
 * "not signed in" (and cancelling) churns the unique periodic work — cancel on
 * Loading, then re-schedule on SignedIn — which surfaces as JobScheduler
 * "cancelled while waiting for bind" and can drop an about-to-run poll
 * (nubecita-1fy.20). Ignoring Loading keeps the already-enqueued work intact.
 *
 * Started once from the production-flavor `AppInitializer` multibinding; the
 * bench flavor never contributes it, so no work is ever enqueued there. The
 * decision is JVM-unit-tested against a fake [DmWorkScheduler]; the actual
 * WorkManager enqueue is behind the seam.
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
            ) { session, enabled ->
                decide(session, enabled).also { decision ->
                    Timber.tag(LOG_TAG).d(
                        "inputs: session=%s enabled=%s -> %s",
                        session.javaClass.simpleName,
                        enabled,
                        decision,
                    )
                }
            }.distinctUntilChanged()
                .collect { decision ->
                    when (decision) {
                        Decision.SCHEDULE -> scheduler.ensureScheduled()
                        Decision.CANCEL -> scheduler.cancel()
                        Decision.IGNORE -> Unit // transient Loading: keep current state
                    }
                }
        }
    }

    private fun decide(
        session: SessionState,
        enabled: Boolean,
    ): Decision =
        if (!enabled) {
            Decision.CANCEL
        } else {
            // Exhaustive over SessionState so a new variant is a compile error here,
            // not a silent fall-through.
            when (session) {
                is SessionState.SignedIn -> Decision.SCHEDULE
                is SessionState.SignedOut -> Decision.CANCEL
                is SessionState.Loading -> Decision.IGNORE // transient: don't churn the schedule
            }
        }

    private enum class Decision { SCHEDULE, CANCEL, IGNORE }

    private companion object {
        const val LOG_TAG = "DmPoll"
    }
}
