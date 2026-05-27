package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationFilterTest {
    @Test
    fun `All filter omits the reasons parameter`() {
        assertNull(NotificationFilter.All.reasons)
    }

    @Test
    fun `Mentions filter sends mention + reply + quote`() {
        assertEquals(
            listOf("mention", "reply", "quote"),
            NotificationFilter.Mentions.reasons,
        )
    }

    @Test
    fun `Reposts filter sends both repost variants`() {
        assertEquals(
            listOf("repost", "repost-via-repost"),
            NotificationFilter.Reposts.reasons,
        )
    }

    @Test
    fun `Follows filter sends only follow`() {
        assertEquals(
            listOf("follow"),
            NotificationFilter.Follows.reasons,
        )
    }

    @Test
    fun `Likes filter sends both like variants`() {
        assertEquals(
            listOf("like", "like-via-repost"),
            NotificationFilter.Likes.reasons,
        )
    }

    @Test
    fun `enum has exactly the slice-1 chip set`() {
        assertEquals(5, NotificationFilter.entries.size)
        assertEquals(
            listOf("All", "Mentions", "Reposts", "Follows", "Likes"),
            NotificationFilter.entries.map { it.name },
        )
    }
}
