package net.kikin.nubecita.feature.widgets.impl.widget

import org.junit.jupiter.api.Assertions.assertEquals
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
}
