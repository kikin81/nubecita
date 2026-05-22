package net.kikin.nubecita.feature.postdetail.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PostDeepLinkKey] and its [toPostDetailRoute] mapper.
 *
 * The mapper is the bridge between alpha03's
 * [androidx.navigation3.runtime.deeplink.UriDeepLinkMatcher] (which
 * decodes the URI placeholders into the intermediate key) and the
 * back-stack-eligible [PostDetailRoute] consumed by
 * `:feature:postdetail:impl`. The matcher tests in
 * `:feature:profile:impl` cover scheme/host/validator behaviour; here
 * we cover the pure URI synthesis in isolation.
 */
class PostDeepLinkKeyTest {
    @Test
    fun handleFormKey_synthesizesHandleFormAtUri() {
        // Strategy #1 from kf6k-parent §"Handle → DID resolution":
        // push the handle-form AT URI and let the appview resolve the
        // handle server-side. No client-side resolution hop.
        val key = PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi")

        val route = key.toPostDetailRoute()

        assertEquals(
            PostDetailRoute(postUri = "at://alice.bsky.social/app.bsky.feed.post/3lkbabcdefghi"),
            route,
        )
    }

    @Test
    fun didFormKey_synthesizesDidFormAtUri() {
        val key = PostDeepLinkKey(handle = "did:plc:abcdefghijklmnopqrstuvwx", rkey = "3lkbabcdefghi")

        val route = key.toPostDetailRoute()

        assertEquals(
            PostDetailRoute(postUri = "at://did:plc:abcdefghijklmnopqrstuvwx/app.bsky.feed.post/3lkbabcdefghi"),
            route,
        )
    }

    @Test
    fun postDeepLinkKey_isANavKey() {
        // UriDeepLinkMatcher's generic constraint is `T : NavKey`, so
        // the intermediate transport key must satisfy that bound even
        // though it never lands on the back stack.
        val key: NavKey = PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi")

        assertEquals(PostDeepLinkKey::class, key::class)
    }

    @Test
    fun postDeepLinkKey_serializesAndRoundTrips() {
        // UriDeepLinkMatcher decodes via kotlinx-serialization against
        // the key's @Serializable contract; the round-trip here proves
        // the serializer is generated and the field names line up with
        // the URI placeholder names (`handle`, `rkey`).
        val key = PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi")

        val encoded = Json.encodeToString(PostDeepLinkKey.serializer(), key)
        val decoded = Json.decodeFromString(PostDeepLinkKey.serializer(), encoded)

        assertEquals(key, decoded)
    }
}
