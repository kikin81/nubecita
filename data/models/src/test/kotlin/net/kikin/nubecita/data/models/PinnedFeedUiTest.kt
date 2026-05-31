package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PinnedFeedUiTest {
    @Test
    fun `each FeedKind fixture constructs and reports its kind`() {
        assertEquals(FeedKind.Following, PinnedFeedUiFixtures.fakeFollowing().kind)
        assertEquals(FeedKind.Generator, PinnedFeedUiFixtures.fakeGenerator().kind)
        assertEquals(FeedKind.List, PinnedFeedUiFixtures.fakeList().kind)
    }

    @Test
    fun `Following fixture carries no remote avatar`() {
        // Following renders the local Home glyph — see the core-feeds spec
        // scenario "Following entry carries no remote avatar".
        assertNull(PinnedFeedUiFixtures.fakeFollowing().avatarUrl)
    }

    @Test
    fun `Generator and List fixtures carry a remote avatar by default`() {
        assertEquals(
            "https://cdn.example/avatars/whats-hot.jpg",
            PinnedFeedUiFixtures.fakeGenerator().avatarUrl,
        )
        assertEquals(
            "https://cdn.example/avatars/my-list.jpg",
            PinnedFeedUiFixtures.fakeList().avatarUrl,
        )
    }

    @Test
    fun `every FeedKind is representable in an exhaustive when without an else branch`() {
        // Adding a future FeedKind without updating this when forces a
        // compile error — the canonical "every dispatch site needs a new
        // arm" surface.
        val labels =
            FeedKind.entries.map { kind ->
                when (kind) {
                    FeedKind.Following -> "following"
                    FeedKind.Generator -> "generator"
                    FeedKind.List -> "list"
                }
            }
        assertEquals(listOf("following", "generator", "list"), labels)
    }

    @Test
    fun `two PinnedFeedUi with identical content are structurally equal`() {
        val a = PinnedFeedUiFixtures.fakeGenerator(id = "g1")
        val b = PinnedFeedUiFixtures.fakeGenerator(id = "g1")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing uri makes feeds unequal`() {
        val a = PinnedFeedUiFixtures.fakeGenerator(uri = "at://a")
        val b = PinnedFeedUiFixtures.fakeGenerator(uri = "at://b")
        assertNotEquals(a, b)
    }
}
