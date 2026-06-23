package net.kikin.nubecita.core.review

import android.app.Activity

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
}
