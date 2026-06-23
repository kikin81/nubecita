package net.kikin.nubecita.core.review

import kotlinx.datetime.Instant

/**
 * Snapshot of the locally stored in-app-review eligibility counters, read once
 * per [ReviewManager.onPostPublished] evaluation. Pure data; [ReviewPolicy]
 * decides eligibility from this snapshot plus the current time.
 *
 * @property firstLaunchAt when the app was first launched on this device, or
 *   `null` if it has never been stamped (treated as not-yet-eligible).
 * @property successfulPostCount lifetime count of successful post publishes.
 * @property requestCount lifetime count of review requests we have launched.
 * @property lastRequestedAt when we last launched a review request, or `null`.
 */
internal data class ReviewState(
    val firstLaunchAt: Instant?,
    val successfulPostCount: Int,
    val requestCount: Int,
    val lastRequestedAt: Instant?,
)
