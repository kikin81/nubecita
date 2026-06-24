package net.kikin.nubecita.core.review

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens the app's Play Store listing — the manual "Rate Nubecita" entry point.
 *
 * This is deliberately a plain store deep link, NOT the in-app review API: per
 * Google's guidance a button-triggered in-app review may render nothing once the
 * user hits the quota, which reads as broken; a store link always works.
 */
object PlayStoreLauncher {
    /**
     * The release applicationId. Hardcoded (rather than read from a runtime
     * `packageName` or `:app`'s `BuildConfig`, which `:core:review` can't see)
     * so the link always points at the published listing.
     */
    const val PACKAGE = "net.kikin.nubecita"

    /** `market://` URI that opens the listing in the Play Store app. */
    fun marketUri(pkg: String = PACKAGE): String = "market://details?id=$pkg"

    /** `https://` listing URL — the fallback when the Play Store app is absent. */
    fun webUrl(pkg: String = PACKAGE): String = "https://play.google.com/store/apps/details?id=$pkg"

    /**
     * Opens the listing in the Play Store app, falling back to the web listing
     * (browser) when the Play Store app is unavailable. Fail-silent if neither
     * resolves — the caller never sees an error.
     */
    fun openListing(
        context: Context,
        pkg: String = PACKAGE,
    ) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(marketUri(pkg)))) }
            .recoverCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl(pkg)))) }
    }
}
