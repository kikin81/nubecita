package net.kikin.nubecita.core.review

import kotlin.time.Instant

/**
 * Persistence seam for the in-app-review eligibility counters, owned by
 * `:core:review` (its own DataStore — the capability is self-contained, like
 * `:core:billing` owns its entitlement state). Faked in `DefaultReviewManager`
 * tests; the real implementation is [DefaultReviewPreferences].
 */
internal interface ReviewPreferences {
    /** Reads the current counter snapshot; a read failure surfaces defaults. */
    suspend fun currentState(): ReviewState

    /** Increments the lifetime successful-post counter by one. */
    suspend fun incrementPostCount()

    /** Records that a review request was launched at [now] (count + timestamp). */
    suspend fun recordReviewRequested(now: Instant)

    /** Stamps the first-launch time to [now] only if it has never been set. */
    suspend fun stampFirstLaunchIfUnset(now: Instant)
}
