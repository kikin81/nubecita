package net.kikin.nubecita.feature.widgets.impl.widget

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WidgetDeepLinksTest {
    @Test
    fun `builds the profile-post deep link from a did-authority at-uri`() {
        assertEquals(
            "nubecita://profile/did:plc:abc/post/3lkbabcdefghi",
            widgetPostDeepLink("at://did:plc:abc/app.bsky.feed.post/3lkbabcdefghi"),
        )
    }

    @Test
    fun `builds the deep link from a handle-authority at-uri`() {
        assertEquals(
            "nubecita://profile/alice.bsky.social/post/3lkb123",
            widgetPostDeepLink("at://alice.bsky.social/app.bsky.feed.post/3lkb123"),
        )
    }

    @Test
    fun `returns null for a malformed or non-post at-uri`() {
        assertNull(widgetPostDeepLink("invalid-uri"))
        assertNull(widgetPostDeepLink("at://did:plc:abc")) // no collection / rkey
        assertNull(widgetPostDeepLink("at://did:plc:abc/app.bsky.feed.like/123")) // wrong collection
        assertNull(widgetPostDeepLink("at://did:plc:abc/app.bsky.feed.post/")) // empty rkey
        assertNull(widgetPostDeepLink("at:///app.bsky.feed.post/123")) // empty authority
    }
}
