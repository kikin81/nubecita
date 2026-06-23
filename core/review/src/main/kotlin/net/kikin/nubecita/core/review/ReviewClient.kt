package net.kikin.nubecita.core.review

import android.app.Activity

/**
 * Opaque token returned by [ReviewClient.requestReview] and consumed by
 * [ReviewClient.launchReview]. Wraps the Play `ReviewInfo` without leaking the
 * Play type into the manager; the manager only shuttles it between the two
 * calls. [raw] is `Any` so fakes can construct a handle without a real
 * `ReviewInfo`.
 */
internal class ReviewHandle(
    internal val raw: Any,
)

/**
 * Internal seam over the Google Play review API, split into request + launch so
 * [DefaultReviewManager] can record the attempt between them (the quota keys on
 * the request, and there is no submission signal). Faked in manager tests;
 * exercised against Google's `FakeReviewManager` in `ReviewClientTest`.
 */
internal interface ReviewClient {
    /** Obtains a review flow handle from Play; throws on failure (offline, no Play Store). */
    suspend fun requestReview(activity: Activity): ReviewHandle

    /** Launches the review UI for a previously obtained [handle]; throws on failure. */
    suspend fun launchReview(
        activity: Activity,
        handle: ReviewHandle,
    )
}
