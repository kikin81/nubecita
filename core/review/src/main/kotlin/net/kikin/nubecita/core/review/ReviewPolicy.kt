package net.kikin.nubecita.core.review

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Pure, side-effect-free eligibility predicate for the in-app review prompt.
 *
 * Conservative gate (design D4): only retained, engaged users are asked, and
 * never too often. All elapsed-time comparisons use exact [kotlin.time.Duration]
 * arithmetic — not calendar days — to stay timezone/region/reinstall-safe.
 */
internal object ReviewPolicy {
    /** Minimum lifetime successful posts before the first request. */
    const val MIN_POSTS = 3

    /** Minimum age since first install before the first request. */
    val MIN_AGE = 3.days

    /** Hard lifetime cap on the number of review requests we will make. */
    const val MAX_LIFETIME_REQUESTS = 3

    /** Minimum time between two requests. */
    val COOLDOWN = 90.days

    fun isEligible(
        state: ReviewState,
        firstInstallTime: Instant,
        now: Instant,
    ): Boolean =
        state.successfulPostCount >= MIN_POSTS &&
            (now - firstInstallTime) >= MIN_AGE &&
            state.requestCount < MAX_LIFETIME_REQUESTS &&
            (state.lastRequestedAt == null || (now - state.lastRequestedAt) >= COOLDOWN)
}
