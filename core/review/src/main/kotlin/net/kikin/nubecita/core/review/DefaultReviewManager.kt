package net.kikin.nubecita.core.review

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

/**
 * Orchestrates the post-publish in-app review request (design D5/D6):
 *
 * 1. Increment the successful-post counter (always).
 * 2. If [ReviewPolicy] says ineligible, stop.
 * 3. Request the review flow; on success **record the attempt** (the Play quota
 *    keys on the request and there is no submission signal, so a request — not a
 *    completed review — is the spent attempt), then launch the review UI.
 *
 * A failed *request* is not recorded (a later eligible publish retries — no
 * storm); a failed *launch* keeps the recorded attempt (Play already counted
 * it). Everything runs on [dispatcher] (off the main thread) and is fail-silent:
 * any Play/storage error is debug-logged, never surfaced.
 */
internal class DefaultReviewManager
    @Inject
    constructor(
        private val reviewClient: ReviewClient,
        private val preferences: ReviewPreferences,
        private val installTimeProvider: InstallTimeProvider,
        private val clock: Clock,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ReviewManager {
        override suspend fun onPostPublished(activity: Activity) {
            withContext(dispatcher) {
                runCatching {
                    preferences.incrementPostCount()
                    val state = preferences.currentState()
                    val now = clock.now()
                    if (!ReviewPolicy.isEligible(state, installTimeProvider.firstInstallTime(), now)) return@runCatching

                    // requestReview may throw (offline / no Play Store) → caught
                    // below, NOT recorded, so a later eligible publish retries.
                    val handle = reviewClient.requestReview(activity)
                    preferences.recordReviewRequested(now)

                    // The attempt is already spent; a launch failure is swallowed.
                    runCatching { reviewClient.launchReview(activity, handle) }
                        .onFailure {
                            it.rethrowIfCancellation()
                            Timber.tag(TAG).w(it, "launchReview failed")
                        }
                }.onFailure {
                    it.rethrowIfCancellation()
                    Timber.tag(TAG).w(it, "onPostPublished failed")
                }
            }
        }

        // `runCatching` catches everything, including CancellationException —
        // swallowing it would break cooperative cancellation when the host
        // Activity scope is cancelled. Rethrow it; log only genuine failures.
        // Logged at `w` (not `d`) so integration/storage issues are visible in
        // logcat, and not `e` so expected offline failures don't reach
        // Crashlytics — matches `DefaultModerationRepository`.
        private fun Throwable.rethrowIfCancellation() {
            if (this is CancellationException) throw this
        }

        private companion object {
            const val TAG = "ReviewManager"
        }
    }
