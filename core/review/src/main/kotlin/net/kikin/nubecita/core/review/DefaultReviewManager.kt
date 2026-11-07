package net.kikin.nubecita.core.review

import android.app.Activity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.ReviewRequested
import net.kikin.nubecita.core.analytics.ReviewTrigger
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
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
        private val analytics: AnalyticsClient,
    ) : ReviewManager {
        override suspend fun onPostPublished(activity: Activity) {
            withContext(dispatcher) {
                runCatchingCancellable { preferences.incrementPostCount() }
                    .onFailure { Timber.tag(TAG).w(it, "incrementPostCount failed") }
            }
            onPositiveMoment(activity, ReviewTrigger.Post)
        }

        override suspend fun onPositiveMoment(
            activity: Activity,
            trigger: ReviewTrigger,
        ) {
            withContext(dispatcher) {
                runCatchingCancellable {
                    val state = preferences.currentState()
                    val now = clock.now()
                    if (!ReviewPolicy.isEligible(state, installTimeProvider.firstInstallTime(), now)) return@runCatchingCancellable

                    // requestReview may throw (offline / no Play Store) → caught
                    // below, NOT recorded, so a later eligible moment retries.
                    val handle = reviewClient.requestReview(activity)
                    preferences.recordReviewRequested(now)
                    analytics.log(ReviewRequested(trigger))

                    // The attempt is already spent; a launch failure is swallowed.
                    runCatchingCancellable { reviewClient.launchReview(activity, handle) }
                        .onFailure {
                            Timber.tag(TAG).w(it, "launchReview failed")
                        }
                }.onFailure {
                    Timber.tag(TAG).w(it, "onPositiveMoment failed")
                }
            }
        }

        override suspend fun recordActiveDay() {
            withContext(dispatcher) {
                // The reader's local day, so a late-night session and the next
                // morning count as two days where the reader lives.
                val today =
                    clock
                        .now()
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                        .date
                        .toEpochDays()
                runCatchingCancellable { preferences.recordActiveDay(today.toLong()) }
                    .onFailure { Timber.tag(TAG).w(it, "recordActiveDay failed") }
            }
        }

        override suspend fun recordLike() {
            withContext(dispatcher) {
                runCatchingCancellable { preferences.recordLike() }
                    .onFailure { Timber.tag(TAG).w(it, "recordLike failed") }
            }
        }

        // Failures log at `w` (not `d`) so integration/storage issues are visible
        // in logcat, and not `e` so an expected offline failure does not reach
        // Crashlytics — matches `DefaultModerationRepository`.
        private companion object {
            const val TAG = "ReviewManager"
        }
    }
