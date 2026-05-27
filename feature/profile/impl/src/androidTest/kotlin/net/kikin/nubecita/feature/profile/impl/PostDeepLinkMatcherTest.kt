package net.kikin.nubecita.feature.profile.impl

import android.content.Intent
import androidx.navigation3.runtime.deeplink.DeepLinkRequest
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.profile.impl.di.ProfileDeepLinkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the post-detail deep-link matchers contributed by
 * [ProfileDeepLinkModule] against the kf6k.3 acceptance shapes plus
 * the kf6k.5 security-checklist rejections.
 *
 * Why instrumented and not JVM: the alpha03
 * [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher] resolves
 * its URI patterns through `android.net.Uri.parse` and matches with
 * `Uri.getScheme()` / `Uri.getPathSegments()`. Those APIs need the
 * Android runtime, so the matcher contract is verified on a real
 * Android image rather than via JVM unit tests (kf6k.4 §"Trade-offs
 * accepted"). The mapper logic ([PostDeepLinkKey.toPostDetailRoute])
 * and rkey validator ([isValidRkey]) are unit-tested separately.
 */
@RunWith(AndroidJUnit4::class)
class PostDeepLinkMatcherTest {
    private val nubecitaAppMatcher = ProfileDeepLinkModule.provideNubecitaAppPostDeepLinkMatcher()
    private val httpsMatcher = ProfileDeepLinkModule.provideHttpsPostDeepLinkMatcher()
    private val nubecitaMatcher = ProfileDeepLinkModule.provideNubecitaPostDeepLinkMatcher()

    private fun viewRequest(uri: String): DeepLinkRequest = DeepLinkRequest.fromUriString(uri, null, Intent.ACTION_VIEW)

    // -- HTTPS verified App Link (nubecita.app) — accept cases ------------

    @Test
    fun https_nubecitaAppPostHandleRkey_matchesPostDeepLinkKey() {
        val matched =
            nubecitaAppMatcher.match(viewRequest("https://nubecita.app/profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertEquals(
            PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi"),
            matched,
        )
    }

    @Test
    fun https_nubecitaAppPostDidRkey_matchesPostDeepLinkKey() {
        val matched =
            nubecitaAppMatcher.match(
                viewRequest("https://nubecita.app/profile/did:plc:abcdefghijklmnopqrstuvwx/post/3lkbabcdefghi"),
            )
        assertEquals(
            PostDeepLinkKey(handle = "did:plc:abcdefghijklmnopqrstuvwx", rkey = "3lkbabcdefghi"),
            matched,
        )
    }

    // -- HTTPS chooser (bsky.app) — accept cases --------------------------

    @Test
    fun https_bskyAppPostHandleRkey_matchesPostDeepLinkKey() {
        val matched =
            httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertEquals(
            PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi"),
            matched,
        )
    }

    @Test
    fun https_bskyAppPostDidRkey_matchesPostDeepLinkKey() {
        val matched =
            httpsMatcher.match(
                viewRequest("https://bsky.app/profile/did:plc:abcdefghijklmnopqrstuvwx/post/3lkbabcdefghi"),
            )
        assertEquals(
            PostDeepLinkKey(handle = "did:plc:abcdefghijklmnopqrstuvwx", rkey = "3lkbabcdefghi"),
            matched,
        )
    }

    // -- Custom scheme — accept case --------------------------------------

    @Test
    fun nubecita_postHandleRkey_matchesPostDeepLinkKey() {
        val matched =
            nubecitaMatcher.match(viewRequest("nubecita://profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertEquals(
            PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi"),
            matched,
        )
    }

    @Test
    fun nubecita_postDidRkey_matchesPostDeepLinkKey() {
        // Push notifications construct nubecita:// URIs with the author's
        // DID in the {handle} slot (via AtUriToDeepLink). Regression coverage
        // for nubecita-1fy.6 — the smoke caught taps landing on the feed
        // instead of PostDetail; before this test existed, the matcher set
        // had no DID-in-custom-scheme coverage so the gap shipped unnoticed.
        val matched =
            nubecitaMatcher.match(
                viewRequest("nubecita://profile/did:plc:abcdefghijklmnopqrstuvwx/post/3lkbabcdefghi"),
            )
        assertEquals(
            PostDeepLinkKey(handle = "did:plc:abcdefghijklmnopqrstuvwx", rkey = "3lkbabcdefghi"),
            matched,
        )
    }

    // -- Cross-host rejection — each matcher claims only its host ---------

    @Test
    fun nubecitaAppPostMatcher_doesNotClaim_bskyAppUri() {
        val matched =
            nubecitaAppMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertNull(matched)
    }

    @Test
    fun bskyAppPostMatcher_doesNotClaim_nubecitaAppUri() {
        val matched =
            httpsMatcher.match(viewRequest("https://nubecita.app/profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertNull(matched)
    }

    // -- Mirror test from ProfileDeepLinkMatcherTest ----------------------

    @Test
    fun https_profileOnly_doesNotMatchPostMatcher() {
        // The /profile/{handle} shape is kf6k.2's job; the post matcher
        // must not partially match it. alpha03's `pathSegments.size`
        // gate short-circuits before the regex.
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social"))
        assertNull(matched)
    }

    // -- Reject cases — wrong shape for *this* matcher --------------------

    @Test
    fun https_wrongHost_doesNotMatch() {
        // Defence-in-depth: the manifest filter constrains host=bsky.app
        // at the OS level, but matcher re-checks. evil.example cannot
        // impersonate bsky.app at this layer.
        val matched =
            httpsMatcher.match(viewRequest("https://evil.example/profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertNull(matched)
    }

    @Test
    fun http_insecure_doesNotMatch() {
        // alpha03 scheme compare is exact; http != https.
        val matched =
            httpsMatcher.match(viewRequest("http://bsky.app/profile/alice.bsky.social/post/3lkbabcdefghi"))
        assertNull(matched)
    }

    @Test
    fun extraTrailingSegment_doesNotMatch() {
        // `/profile/{h}/post/{r}/{extra}` has more segments than the
        // pattern — alpha03's `pathSegments.size` gate rejects.
        val matched =
            httpsMatcher.match(
                viewRequest("https://bsky.app/profile/alice.bsky.social/post/3lkbabcdefghi/extra"),
            )
        assertNull(matched)
    }

    // -- Reject cases — kf6k.5 security checklist -------------------------

    @Test
    fun nonViewAction_isRejectedByIntentActionFilter() {
        val request =
            DeepLinkRequest.fromUriString(
                "https://bsky.app/profile/alice.bsky.social/post/3lkbabcdefghi",
                null,
                Intent.ACTION_SEND,
            )
        assertNull(httpsMatcher.match(request))
    }

    @Test
    fun malformedHandle_singleLabel_isRejectedByValidator() {
        // Single-label "handle" reaches isValidActor → false → matcher
        // returns null even though the path shape is otherwise valid.
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile/alice/post/3lkbabcdefghi"))
        assertNull(matched)
    }

    @Test
    fun malformedRkey_wrongLength_isRejectedByValidator() {
        // Shape matches but rkey is 12 chars — TID grammar requires
        // exactly 13. Validator rejects → matcher returns null.
        val matched =
            httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social/post/3lkbabcdefgh"))
        assertNull(matched)
    }

    @Test
    fun malformedRkey_disallowedChar_isRejectedByValidator() {
        // `0` is excluded from the TID base32-sortable alphabet.
        val matched =
            httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social/post/3lkbabcd0fghi"))
        assertNull(matched)
    }

    @Test
    fun malformedHandle_emptySlot_isRejected() {
        // Empty {handle} placeholder — `/profile//post/3lkb...`.
        // The pathSegments-count gate or the validator rejects.
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile//post/3lkbabcdefghi"))
        assertNull(matched)
    }

    @Test
    fun malformedRkey_emptySlot_isRejected() {
        val matched = httpsMatcher.match(viewRequest("https://bsky.app/profile/alice.bsky.social/post/"))
        assertNull(matched)
    }
}
