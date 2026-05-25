package net.kikin.nubecita.core.push

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import timber.log.Timber

/**
 * Drives push-token (un)registration off the canonical
 * [SessionStateProvider.state] flow. The push module's single entry point for
 * lifecycle plumbing; FCM service / login wiring / settings UI all call
 * through this coordinator rather than directly into
 * [PushRegistrationRepository].
 *
 * **State-flow → action mapping:**
 *
 * - [SessionState.SignedIn] → resolve current FCM token, write `Pending` to
 *   the store, call [PushRegistrationRepository.register], write the
 *   `Succeeded` / `Failed` outcome.
 *   Skipped (no network) when the store already shows
 *   `(accountDid == did, fcmToken == currentToken, status == Succeeded)`.
 *
 * - [SessionState.SignedOut] → cancel any in-flight register loop, then
 *   best-effort [PushRegistrationRepository.unregister] with the
 *   previously-stored credentials, then [PushRegistrationStateStore.clear].
 *   The unregister call is fire-and-forget — a failure (gateway down, 401)
 *   still clears the local store so a re-login can re-register cleanly.
 *
 * - [SessionState.Loading] → no-op. The coordinator waits for the resolved
 *   state.
 *
 * **Cold-start:** [SessionStateProvider.state] is a `StateFlow`, so a fresh
 * subscription receives the current value on first collect — no separate
 * cold-start hook is needed.
 *
 * **Preemption + single-flight register:** the state collector uses
 * `collectLatest`, and all register entry points (`onSessionEstablished`,
 * `onTokenRotated`) funnel through [launchRegister], which holds a
 * [Mutex]-protected reference to the in-flight register job and
 * `cancelAndJoin`s the prior one before launching the next. This guarantees:
 *
 * - A `SignedOut` arriving mid-backoff preempts the loop and the unregister
 *   handler runs immediately instead of waiting out the 5s/30s/2m/8m
 *   schedule.
 * - An `onTokenRotated` arriving mid-backoff cancels the prior (now-stale)
 *   register and starts a fresh one against the new token — no overlap and
 *   no racy `PushRegistrationStateStore` writes between two concurrent
 *   loops.
 *
 * **Retry / backoff:** a register failure schedules a retry on the same
 * coroutine using the cap-at-five-attempts cadence below (one immediate +
 * four delayed). After the cap is reached the store is marked `Failed` and
 * the coordinator waits for the next state emission (or [onTokenRotated])
 * before trying again.
 *
 * [scope] must outlive the process's foreground lifetime — typically the
 * application's own `CoroutineScope` injected at `NubecitaApplication`
 * construction. The coordinator does NOT cancel the scope itself; tearing
 * down the scope cancels both the collector and any in-flight register job.
 */
class PushRegistrationCoordinator(
    private val sessionStateProvider: SessionStateProvider,
    private val repository: PushRegistrationRepository,
    private val stateStore: PushRegistrationStateStore,
    private val tokenProvider: FcmTokenProvider,
    private val scope: CoroutineScope,
) {
    private var collectJob: Job? = null
    private var registerJob: Job? = null
    private val registerJobLock = Mutex()

    /**
     * Starts collecting the session-state flow. Idempotent — calling twice
     * (e.g. from a re-entered Application.onCreate during a hot-restart) is
     * a no-op.
     */
    fun start() {
        if (collectJob?.isActive == true) return
        collectJob =
            scope.launch {
                sessionStateProvider.state.collectLatest { state ->
                    when (state) {
                        is SessionState.Loading -> Unit
                        is SessionState.SignedOut -> {
                            cancelInFlightRegister()
                            onSessionEnded()
                        }
                        is SessionState.SignedIn -> onSessionEstablished(state.did)
                    }
                }
            }
    }

    suspend fun onTokenRotated(token: String) {
        // Fast path: skip the mutex acquire + job-cancel work when there's
        // no session to register against. The inner re-check below is the
        // race-defense; this outer check avoids pointless work on the common
        // signed-out path (e.g. FCM token rotation while the app is logged
        // out at app launch).
        if (sessionStateProvider.state.value !is SessionState.SignedIn) return
        launchRegister {
            // Re-check inside the launched block: between the outer fast-path
            // check and here, a SignedOut emission could have raced through
            // the collector and torn down the local store. If so, skip
            // silently — registering for a session that no longer exists
            // would leak a (DID, token) tuple onto the gateway with no
            // matching client.
            val current = sessionStateProvider.state.value
            if (current is SessionState.SignedIn) {
                registerWithBackoff(did = current.did, fcmToken = token)
            }
        }
    }

    private suspend fun onSessionEstablished(did: String) {
        val tokenResult = runCatchingExceptCancellation { tokenProvider.current() }
        val token =
            tokenResult.getOrElse {
                Timber.tag(TAG).e(it, "FcmTokenProvider.current() failed; deferring registration")
                stateStore.write(
                    PushRegistrationState(
                        accountDid = did,
                        fcmToken = stateStore.read().fcmToken,
                        status = PushRegistrationState.Status.Failed,
                    ),
                )
                return
            }
        val stored = stateStore.read()
        if (
            stored.status == PushRegistrationState.Status.Succeeded &&
            stored.accountDid == did &&
            stored.fcmToken == token
        ) {
            return
        }
        launchRegister { registerWithBackoff(did = did, fcmToken = token) }
    }

    private suspend fun onSessionEnded() {
        val stored = stateStore.read()
        if (stored.accountDid != null && stored.fcmToken != null) {
            // Fire-and-forget — failures during sign-out are not recoverable
            // here (the local session is already torn down) so we drop the
            // result and proceed to clear the store regardless.
            repository.unregister(did = stored.accountDid, fcmToken = stored.fcmToken)
        }
        stateStore.clear()
    }

    private suspend fun cancelInFlightRegister() =
        registerJobLock.withLock {
            registerJob?.cancelAndJoin()
            registerJob = null
        }

    private suspend fun launchRegister(block: suspend () -> Unit) {
        registerJobLock.withLock {
            registerJob?.cancelAndJoin()
            registerJob = scope.launch { block() }
        }
    }

    private suspend fun registerWithBackoff(
        did: String,
        fcmToken: String,
    ) {
        stateStore.write(
            PushRegistrationState(
                accountDid = did,
                fcmToken = fcmToken,
                status = PushRegistrationState.Status.Pending,
            ),
        )
        // Total attempt budget: one immediate, then a retry per BACKOFF_DELAYS_MS.
        for (attempt in 0..BACKOFF_DELAYS_MS.size) {
            if (attempt > 0) delay(BACKOFF_DELAYS_MS[attempt - 1])
            val outcome = repository.register(did = did, fcmToken = fcmToken)
            if (outcome.isSuccess) {
                stateStore.write(
                    PushRegistrationState(
                        accountDid = did,
                        fcmToken = fcmToken,
                        status = PushRegistrationState.Status.Succeeded,
                    ),
                )
                return
            }
        }
        stateStore.write(
            PushRegistrationState(
                accountDid = did,
                fcmToken = fcmToken,
                status = PushRegistrationState.Status.Failed,
            ),
        )
    }

    companion object {
        // 5s, 30s, 2m, 8m — four inter-attempt delays + one immediate attempt
        // = five total tries before giving up and waiting for the next state
        // emission or onTokenRotated. Cadence chosen for the typical
        // transient-failure shape: a few seconds of network blip → a half-
        // minute outage → a couple-minute gateway redeploy → and finally a
        // ten-minute "the device's connectivity is genuinely flaky right now,
        // try the slowest reasonable cadence before backing off entirely."
        private val BACKOFF_DELAYS_MS: List<Long> =
            listOf(
                5_000L,
                30_000L,
                120_000L,
                480_000L,
            )

        private const val TAG = "PushRegistration"
    }
}
