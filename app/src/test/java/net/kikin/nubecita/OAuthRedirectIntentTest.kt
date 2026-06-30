package net.kikin.nubecita

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OAuthRedirectIntentTest {
    @Test
    fun verifiedAppLinkHttpsUriIsAccepted() {
        // The new Android App Link callback that lands in MainActivity after
        // the OS-level Digital Asset Links verification succeeds.
        assertTrue(isOAuthRedirect(scheme = "https", host = "nubecita.app", path = "/oauth-redirect"))
    }

    @Test
    fun legacyCustomSchemeUriIsAccepted() {
        // The pre-App-Link redirect target. Custom-scheme URIs without "//" carry
        // no host component, so the predicate must accept null host on this branch.
        assertTrue(isOAuthRedirect(scheme = "app.nubecita", host = null, path = "/oauth-redirect"))
    }

    // Regression guards for the OAuth callback boundary. The intent filters in
    // AndroidManifest.xml already constrain what the OS will deliver to
    // MainActivity, but the predicate is the in-process re-check that ensures
    // a misconfigured filter or a future unrelated nubecita.app deep link
    // can't leak into oauthRedirectBroker.publish().

    @Test
    fun rejectsHttpsWithWrongHost() {
        assertFalse(isOAuthRedirect(scheme = "https", host = "evil.example", path = "/oauth-redirect"))
    }

    @Test
    fun rejectsHttpsWithWrongPath() {
        assertFalse(isOAuthRedirect(scheme = "https", host = "nubecita.app", path = "/about"))
    }

    @Test
    fun rejectsInsecureHttpEvenOnRightHost() {
        // Insecure http is not a sanctioned redirect target — only the autoVerify
        // HTTPS App Link filter is. Browser → app handoff over http would defeat
        // the whole reason for migrating off the custom scheme.
        assertFalse(isOAuthRedirect(scheme = "http", host = "nubecita.app", path = "/oauth-redirect"))
    }

    @Test
    fun rejectsForeignCustomSchemeWithMatchingPath() {
        // Predecessor scheme from the kikin81.github.io era. Any leftover deep link
        // hitting this app with that scheme is no longer a valid redirect target.
        assertFalse(isOAuthRedirect(scheme = "io.github.kikin81", host = null, path = "/oauth-redirect"))
    }

    @Test
    fun rejectsLegacySchemeWithWrongPath() {
        assertFalse(isOAuthRedirect(scheme = "app.nubecita", host = null, path = "/something-else"))
    }

    @Test
    fun rejectsNullScheme() {
        assertFalse(isOAuthRedirect(scheme = null, host = "nubecita.app", path = "/oauth-redirect"))
    }

    // isMalformedOAuthRedirect — the redirect-path-but-failed-validation gap that
    // MainActivity surfaces as a non-fatal instead of dropping at debug level.

    @Test
    fun malformedRedirect_redirectPathWithWrongHost() {
        // An https App Link on the redirect path but a host isOAuthRedirect rejects
        // → a real OAuth callback we'd otherwise silently drop.
        assertTrue(isMalformedOAuthRedirect(scheme = "https", host = "evil.example", path = "/oauth-redirect"))
    }

    @Test
    fun malformedRedirect_redirectPathWithForeignScheme() {
        assertTrue(isMalformedOAuthRedirect(scheme = "io.github.kikin81", host = null, path = "/oauth-redirect"))
    }

    @Test
    fun notMalformed_validRedirectIsNotFlagged() {
        // A URI that passes isOAuthRedirect is handled normally, never flagged.
        assertFalse(isMalformedOAuthRedirect(scheme = "https", host = "nubecita.app", path = "/oauth-redirect"))
        assertFalse(isMalformedOAuthRedirect(scheme = "app.nubecita", host = null, path = "/oauth-redirect"))
    }

    @Test
    fun notMalformed_unrelatedDeepLinkPathIsNotFlagged() {
        // A non-redirect path is an ordinary deep link, not a malformed callback.
        assertFalse(isMalformedOAuthRedirect(scheme = "https", host = "nubecita.app", path = "/profile/alice"))
    }
}
