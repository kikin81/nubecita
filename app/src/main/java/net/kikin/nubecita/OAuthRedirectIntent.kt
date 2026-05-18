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
    if (path != OAUTH_REDIRECT_PATH) return false
    return when (scheme) {
        OAUTH_REDIRECT_LEGACY_SCHEME -> true
        OAUTH_REDIRECT_APPLINK_SCHEME -> host == OAUTH_REDIRECT_APPLINK_HOST
        else -> false
    }
}

internal const val OAUTH_REDIRECT_PATH: String = "/oauth-redirect"

/** Legacy custom-scheme redirect — reversed FQDN of the OAuth client_id host. */
internal const val OAUTH_REDIRECT_LEGACY_SCHEME: String = "app.nubecita"

/** Verified Android App Link redirect — matches the manifest intent filter for nubecita.app. */
internal const val OAUTH_REDIRECT_APPLINK_SCHEME: String = "https"
internal const val OAUTH_REDIRECT_APPLINK_HOST: String = "nubecita.app"
