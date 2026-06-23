package net.kikin.nubecita.core.review

import kotlin.time.Instant

/**
 * Snapshot of the locally stored in-app-review counters, read once per
 * [ReviewManager.onPostPublished] evaluation. Pure data; [ReviewPolicy] decides
 * eligibility from this snapshot plus the OS-provided first-install time and the
 * current time.
 *
 * The app's age is NOT stored here — it's derived from the OS install time
 * (see [InstallTimeProvider]), which correctly reflects long-time users on
 * upgrade and survives app-data clears.
 *
 * @property successfulPostCount lifetime count of successful post publishes.
 * @property requestCount lifetime count of review requests we have launched.
 * @property lastRequestedAt when we last launched a review request, or `null`.
 */
internal data class ReviewState(
    val successfulPostCount: Int,
    val requestCount: Int,
    val lastRequestedAt: Instant?,
)
