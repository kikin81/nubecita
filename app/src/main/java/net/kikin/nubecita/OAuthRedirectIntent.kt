package net.kikin.nubecita

/**
 * Pure predicate that decides whether an inbound deep link is one of the
 * sanctioned OAuth redirect targets.
 *
 * Defense-in-depth: the manifest intent filters already constrain
 * scheme + host + path at the OS level, but this re-validation prevents
 * a misconfigured filter (or a future unrelated deep link sharing a
 * scheme / host) from leaking through into the OAuth completeLogin path.
 *
 * Kept as a pure top-level function over String inputs (not `android.net.Uri`)
 * so it can be exercised from a JVM unit test without Robolectric.
 */
internal fun isOAuthRedirect(
    scheme: String?,
    host: String?,
    path: String?,
): Boolean {
    if (!isRedirectPath(path)) return false
    return when (scheme) {
        OAUTH_REDIRECT_LEGACY_SCHEME -> true
        OAUTH_REDIRECT_APPLINK_SCHEME -> host == OAUTH_REDIRECT_APPLINK_HOST
        else -> false
    }
}

/**
 * Matches the redirect path with OR without a trailing slash. The verified App
 * Link callback arrives at `https://nubecita.app/oauth-redirect/` (the
 * trailing-slash canonical we now register as the redirect_uri — GitHub Pages
 * serves `/oauth-redirect` as a directory and 301s the no-slash form, which
 * corrupted the App Link handoff and dropped the `code`; nubecita-o4rv.1). The
 * custom-scheme fallback still forwards `app.nubecita:/oauth-redirect` with no
 * slash, so both shapes must be accepted.
 */
private fun isRedirectPath(path: String?): Boolean = path == OAUTH_REDIRECT_PATH || path == "$OAUTH_REDIRECT_PATH/"

/**
 * True when an inbound URI carries the sanctioned OAuth redirect *path* but
 * fails [isOAuthRedirect] (wrong scheme / host). The OS only delivers URIs that
 * matched a manifest intent filter, so this fires on configuration drift — a
 * filter shape that [isOAuthRedirect] doesn't accept — where a real OAuth
 * callback would otherwise fall through to the deep-link branch and be logged at
 * `debug` level (invisible to Crashlytics). Catching it lets [MainActivity]
 * surface the callback as a non-fatal instead of silently dropping it.
 *
 * Pure predicate over String inputs so it's JVM-unit-testable without Robolectric.
 */
internal fun isMalformedOAuthRedirect(
    scheme: String?,
    host: String?,
    path: String?,
): Boolean = isRedirectPath(path) && !isOAuthRedirect(scheme = scheme, host = host, path = path)

internal const val OAUTH_REDIRECT_PATH: String = "/oauth-redirect"

/** Legacy custom-scheme redirect — reversed FQDN of the OAuth client_id host. */
internal const val OAUTH_REDIRECT_LEGACY_SCHEME: String = "app.nubecita"

/** Verified Android App Link redirect — matches the manifest intent filter for nubecita.app. */
internal const val OAUTH_REDIRECT_APPLINK_SCHEME: String = "https"
internal const val OAUTH_REDIRECT_APPLINK_HOST: String = "nubecita.app"
