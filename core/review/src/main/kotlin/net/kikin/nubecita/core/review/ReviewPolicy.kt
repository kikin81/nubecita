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

    /** Minimum distinct days with the app open, for a reader who doesn't post. */
    const val MIN_ACTIVE_DAYS = 5

    /** Minimum lifetime likes, for a reader who doesn't post. */
    const val MIN_LIKES = 3

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
        // Two ways in: writing posts, or reading on enough days to have a
        // view. Most people only read, so the post path alone never asked them.
        (state.successfulPostCount >= MIN_POSTS || isEngagedReader(state)) &&
            (now - firstInstallTime) >= MIN_AGE &&
            state.requestCount < MAX_LIFETIME_REQUESTS &&
            (state.lastRequestedAt == null || (now - state.lastRequestedAt) >= COOLDOWN)

    private fun isEngagedReader(state: ReviewState): Boolean = state.activeDayCount >= MIN_ACTIVE_DAYS && state.likeCount >= MIN_LIKES
}
