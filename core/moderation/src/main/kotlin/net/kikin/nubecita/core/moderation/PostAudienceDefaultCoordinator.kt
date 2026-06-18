package net.kikin.nubecita.core.moderation

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
 * Drives [PostAudienceDefaultRepository.refresh] / `resetToDefault` off the
 * canonical [SessionStateProvider.state] flow — the lifecycle counterpart the
 * post-audience repository was missing (it shipped in nubecita-33bw.4 without
 * one; the composer that consumes the default lands in .5, so this is wired here).
 *
 * Mirrors [ModerationPreferencesCoordinator]: [start] is contributed to the app's
 * `AppInitializer` multibinding (production flavor only).
 *
 * - [SessionState.SignedIn] → [PostAudienceDefaultRepository.refresh]; a failure
 *   is logged (error identity only) and swallowed — the repo keeps its current
 *   (seeded `DEFAULT` or last-good) value and the next emission retries.
 * - [SessionState.SignedOut] → [PostAudienceDefaultRepository.resetToDefault] so
 *   the next account never pre-fills the composer with the previous account's
 *   default (the repo is an app-scoped singleton).
 * - [SessionState.Loading] → no-op.
 *
 * [scope] must outlive the process's foreground lifetime — the application's own
 * `CoroutineScope`. The coordinator does NOT cancel the scope itself.
 */
@Singleton
class PostAudienceDefaultCoordinator
    @Inject
    constructor(
        private val sessionStateProvider: SessionStateProvider,
        private val repository: PostAudienceDefaultRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        private var collectJob: Job? = null

        /**
         * Starts collecting the session-state flow. Idempotent — a second call
         * (e.g. a hot-restart re-entering `Application.onCreate`) is a no-op. The
         * check-then-act is unsynchronized: the only caller is the app's
         * single-threaded `AppInitializer` pass.
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
                                    Timber.tag(TAG).w(
                                        throwable,
                                        "post-audience default refresh failed: %s",
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
            const val TAG = "PostAudienceDefault"
        }
    }
