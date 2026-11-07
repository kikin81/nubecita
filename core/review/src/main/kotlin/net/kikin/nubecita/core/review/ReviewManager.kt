package net.kikin.nubecita.core.review

import android.app.Activity
import net.kikin.nubecita.core.analytics.ReviewTrigger

/**
 * SDK-agnostic boundary for the in-app review prompt — the only review type
 * that escapes `:core:review`. Mirrors `:core:billing`: the [activity] is
 * supplied by the Composable layer (never a ViewModel, which has no Activity
 * handle) and MUST outlive the call, so callers launch it on the host
 * Activity's scope rather than a screen/ViewModel scope.
 */
interface ReviewManager {
    /**
     * Call after a post publish succeeds. Increments the successful-post
     * counter and, if the eligibility policy passes, requests and launches the
     * Google Play in-app review flow. Always fail-silent: any Play or storage
     * error is swallowed and never surfaced to the user.
     */
    suspend fun onPostPublished(activity: Activity)

    /**
     * Call at a positive moment for someone who may never post: after a
     * follow, on closing the gallery or fullscreen player, on coming back from
     * sharing. Requests a review only when the eligibility policy passes, and
     * is fail-silent like [onPostPublished]. Prefer
     * [requestReviewIfEligible], which supplies the Activity's own scope.
     */
    suspend fun onPositiveMoment(
        activity: Activity,
        trigger: ReviewTrigger,
    )

    /** Counts today as an active day. Cheap and idempotent within a day. */
    suspend fun recordActiveDay()

    /** Counts a like, the reader path's proof of real use. */
    suspend fun recordLike()
}
