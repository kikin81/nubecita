package net.kikin.nubecita.core.moderation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives [ModerationPreferencesRepository.refresh] off the canonical
 * [SessionStateProvider.state] flow. The moderation module's single lifecycle
 * entry point: [start] is wired into the app's `AppInitializer` multibinding
 * (production flavor only) so the user's content-filter preferences load once
 * the session resolves to [SessionState.SignedIn].
 *
 * **Why a refresh is needed.** [ModerationPreferencesRepository.prefs] seeds at
 * [ModerationPrefs.DEFAULT] (adult content **off**) so any reader that observes
 * before the first refresh fails safe — adult media is hidden, never shown, on
 * a cold cache. But that also means the viewer's *real* preferences (adult on,
 * per-category visibility) never take effect until something calls [refresh].
 * This coordinator is that something.
 *
 * **State-flow → action mapping:**
 *
 * - [SessionState.SignedIn] → [ModerationPreferencesRepository.refresh]. A
 *   failure is logged (error identity only) and swallowed — the repo keeps its
 *   current (fail-safe DEFAULT or last-good) value, and the next session
 *   emission retries.
 * - [SessionState.SignedOut] → [ModerationPreferencesRepository.resetToDefault]
 *   so the next account never reads the previous account's prefs in the window
 *   before its own refresh lands (the repo is an app-scoped singleton).
 * - [SessionState.Loading] → no-op.
 *
 * **Cold-start + re-login.** [SessionStateProvider.state] is a `StateFlow`, so a
 * fresh subscription receives the current value on first collect — no separate
 * cold-start hook is needed. `collectLatest` means a new sign-in (a different
 * DID) preempts any in-flight refresh and re-runs against the new account.
 *
 * [scope] must outlive the process's foreground lifetime — the application's
 * own `CoroutineScope`. The coordinator does NOT cancel the scope itself.
 */
@Singleton
class ModerationPreferencesCoordinator
    @Inject
    constructor(
        private val sessionStateProvider: SessionStateProvider,
        private val repository: ModerationPreferencesRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private var collectJob: Job? = null

        /**
         * Starts collecting the session-state flow. Idempotent — calling twice
         * (e.g. from a re-entered `Application.onCreate` during a hot-restart)
         * is a no-op. The check-then-act on [collectJob] is unsynchronized: the
         * only caller is the app's single-threaded `AppInitializer` pass, so two
         * concurrent `start()`s never race.
         */
        fun start() {
            if (collectJob?.isActive == true) return
            collectJob =
                scope.launch {
                    sessionStateProvider.state.collectLatest { state ->
                        when (state) {
                            is SessionState.SignedIn ->
                                try {
                                    repository.refresh()
                                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                                    throw cancellation
                                } catch (throwable: Throwable) {
                                    // Keep the fail-safe DEFAULT / last-good prefs; the next
                                    // session emission retries. Log only the error identity
                                    // (the response may carry account-shaped data).
                                    Timber.tag(TAG).w(
                                        throwable,
                                        "moderation prefs refresh failed: %s",
                                        throwable.javaClass.name,
                                    )
                                }
                            // Fail safe on sign-out so the next account can't read this
                            // account's prefs before its own refresh lands.
                            is SessionState.SignedOut -> repository.resetToDefault()
                            is SessionState.Loading -> Unit
                        }
                    }
                }
        }

        private companion object {
            const val TAG = "ModerationPrefs"
        }
    }
