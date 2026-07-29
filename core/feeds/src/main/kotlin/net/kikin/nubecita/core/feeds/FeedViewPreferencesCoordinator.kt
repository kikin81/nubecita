package net.kikin.nubecita.core.feeds

import kotlinx.coroutines.CancellationException
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
 * Drives [FeedViewPreferencesRepository.refresh] off the canonical
 * [SessionStateProvider.state] flow, mirroring
 * `ModerationPreferencesCoordinator`. [start] is wired into the app's
 * `AppInitializer` multibinding (production flavor only).
 *
 * **State-flow → action mapping:**
 * - [SessionState.SignedIn] → [FeedViewPreferencesRepository.refresh]. Failures
 *   are logged (error identity only) and swallowed so the collector survives and
 *   the next session emission retries; the repo keeps its DEFAULT / last-good
 *   value meanwhile.
 * - [SessionState.SignedOut] → [FeedViewPreferencesRepository.resetToDefault].
 * - [SessionState.Loading] → no-op.
 *
 * [scope] must outlive the process's foreground lifetime — the application's own
 * `CoroutineScope`. The coordinator does NOT cancel the scope itself.
 */
@Singleton
class FeedViewPreferencesCoordinator
    @Inject
    constructor(
        private val sessionStateProvider: SessionStateProvider,
        private val repository: FeedViewPreferencesRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private var collectJob: Job? = null

        /**
         * Starts collecting the session-state flow. Idempotent — the
         * check-then-act on [collectJob] is unsynchronized because the only
         * caller is the app's single-threaded `AppInitializer` pass.
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
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (throwable: Throwable) {
                                    // Keep DEFAULT / last-good prefs; the next session
                                    // emission retries.
                                    //
                                    // The throwable itself is deliberately NOT passed to
                                    // Timber: that would log its message and stack trace,
                                    // and a deserialization failure quotes the offending
                                    // JSON — i.e. fragments of the viewer's own
                                    // preferences payload. The class name is enough to
                                    // tell a transport failure from a decode failure.
                                    Timber.tag(TAG).w(
                                        "feed view prefs refresh failed: %s",
                                        throwable.javaClass.name,
                                    )
                                }
                            is SessionState.SignedOut -> repository.resetToDefault()
                            is SessionState.Loading -> Unit
                        }
                    }
                }
        }

        private companion object {
            const val TAG = "FeedViewPrefs"
        }
    }
