package net.kikin.nubecita.feature.profile.impl

import android.content.Intent
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.di.ProfileDeepLinkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the Profile deep-link matchers contributed by
 * [ProfileDeepLinkModule] against the four acceptance shapes in
 * nubecita-kf6k.2 plus the kf6k.5 security-checklist rejections.
 *
 * Why instrumented and not JVM: the alpha03
 * [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher] resolves
 * its URI patterns through `android.net.Uri.parse` and matches with
 * `Uri.getScheme()` / `Uri.getPathSegments()`. Those APIs need the
 * Android runtime, so the matcher contract is verified on a real
 * Android image rather than via JVM unit tests (kf6k.4 §"Trade-offs
 * accepted"). The validator logic and other JVM-friendly pieces are
 * unit-tested separately in [ActorValidationTest].
 */
@RunWith(AndroidJUnit4::class)
class ProfileDeepLinkMatcherTest {
    private val nubecitaAppMatcher = ProfileDeepLinkModule.provideNubecitaAppProfileDeepLinkMatcher()
    private val httpsMatcher = ProfileDeepLinkModule.provideHttpsProfileDeepLinkMatcher()
    private val nubecitaMatcher = ProfileDeepLinkModule.provideNubecitaProfileDeepLinkMatcher()

    private fun viewRequest(uri: String): DeepLinkRequest = DeepLinkRequest.fromUriString(uri, null, Intent.ACTION_VIEW)

    // -- HTTPS verified App Link (nubecita.app) — accept cases ------------

    @Test
    fun https_nubecitaAppProfileHandle_matchesProfileWithHandle() {
        val matched = nubecitaAppMatcher.match(viewRequest("https://nubecita.app/profile/alice.bsky.social"))
        assertEquals(Profile(handle = "alice.bsky.social"), matched)
    }

    @Test
    fun https_nubecitaAppProfileDid_matchesProfileWithDid() {
        val matched =
            nubecitaAppMatcher.match(viewRequest("https://nubecita.app/profile/did:plc:abcdefghijklmnopqrstuvwx"))
        assertEquals(Profile(handle = "did:plc:abcdefghijklmnopqrstuvwx"), matched)
    }

    // -- HTTPS chooser (bsky.app) — accept cases --------------------------

    @Test
    fun https_bskyAppProfileHandle_matchesProfileWithHandle() {
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social"))
        assertEquals(Profile(handle = "alice.bsky.social"), matched)
    }

    @Test
    fun https_bskyAppProfileDid_matchesProfileWithDid() {
        val matched =
            httpsMatcher.match(viewRequest("https://bsky.app/profile/did:plc:abcdefghijklmnopqrstuvwx"))
        assertEquals(Profile(handle = "did:plc:abcdefghijklmnopqrstuvwx"), matched)
    }

    // -- Custom scheme — accept case --------------------------------------

    @Test
    fun nubecita_profileHandle_matchesProfileWithHandle() {
        val matched = nubecitaMatcher.match(viewRequest("nubecita://profile/alice.bsky.social"))
        assertEquals(Profile(handle = "alice.bsky.social"), matched)
    }

    // -- Cross-host rejection — each matcher claims only its host ---------

    @Test
    fun nubecitaAppMatcher_doesNotClaim_bskyAppUri() {
        val matched = nubecitaAppMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social"))
        assertNull(matched)
    }

    @Test
    fun bskyAppMatcher_doesNotClaim_nubecitaAppUri() {
        val matched = httpsMatcher.match(viewRequest("https://nubecita.app/profile/alice.bsky.social"))
        assertNull(matched)
    }

    // -- Reject cases — wrong shape for *this* matcher --------------------

    @Test
    fun https_postSuffix_doesNotMatchProfileMatcher() {
        // The /profile/{handle}/post/{rkey} shape is kf6k.3's job; the
        // profile matcher must not partially match it. alpha03's
        // `pathSegments.size` gate short-circuits before the regex.
        val matched =
            httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social/post/3lkbabc"))
        assertNull(matched)
    }

    @Test
    fun https_wrongHost_doesNotMatch() {
        // Defence-in-depth: the manifest filter constrains host=bsky.app
        // at the OS level, but matcher re-checks. evil.example cannot
        // impersonate bsky.app at this layer.
        val matched = httpsMatcher.match(viewRequest("https://evil.example/profile/alice.bsky.social"))
        assertNull(matched)
    }

    @Test
    fun http_insecure_doesNotMatch() {
        // alpha03 scheme compare is exact; http != https.
        val matched = httpsMatcher.match(viewRequest("http://bsky.app/profile/alice.bsky.social"))
        assertNull(matched)
    }

    // -- Reject cases — kf6k.5 security checklist -------------------------

    @Test
    fun nonViewAction_isRejectedByIntentActionFilter() {
        // ACTION_SEND at the same URI must not match — the
        // IntentActionFilter constrains to ACTION_VIEW.
        val request =
            DeepLinkRequest.fromUriString(
                "https://bsky.app/profile/alice.bsky.social",
                null,
                Intent.ACTION_SEND,
            )
        assertNull(httpsMatcher.match(request))
    }

    @Test
    fun malformedHandle_singleLabel_isRejectedByValidator() {
        // The matcher's path regex accepts any non-/ run, so a
        // single-label "handle" reaches the validator. Validator
        // rejects → matcher returns null.
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile/alice"))
        assertNull(matched)
    }

    @Test
    fun malformedHandle_emptySlot_isRejected() {
        // Empty placeholder — UriDeepLinkMatcher would decode handle="".
        // Either the pathSegments-count gate or the validator rejects;
        // the contract is that null comes back either way.
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile/"))
        assertNull(matched)
    }
}
