package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NotificationItemUiFixturesTest {
    @Test
    fun `singleLike returns a Single Like with one actor`() {
        val item = NotificationItemUiFixtures.singleLike()
        assertEquals(NotificationReason.Like, item.reason)
        assertEquals(1, item.actors.size)
        assertNotNull(item.subjectPost)
    }

    @Test
    fun `aggregatedLikes returns an Aggregated Like with N actors`() {
        val item = NotificationItemUiFixtures.aggregatedLikes(actorCount = 5)
        assertEquals(NotificationReason.Like, item.reason)
        assertEquals(5, item.actors.size)
    }

    @Test
    fun `singleFollow has no subject post`() {
        val item = NotificationItemUiFixtures.singleFollow()
        assertEquals(NotificationReason.Follow, item.reason)
        assertNull(item.subjectPost)
    }

    @Test
    fun `aggregatedFollows with 3 actors has no subject post`() {
        val item = NotificationItemUiFixtures.aggregatedFollows(actorCount = 3)
        assertEquals(NotificationReason.Follow, item.reason)
        assertEquals(3, item.actors.size)
        assertNull(item.subjectPost)
    }

    @Test
    fun `singleReply singleQuote singleMention each carry the right reason and a subject post`() {
        assertEquals(NotificationReason.Reply, NotificationItemUiFixtures.singleReply().reason)
        assertNotNull(NotificationItemUiFixtures.singleReply().subjectPost)
        assertEquals(NotificationReason.Quote, NotificationItemUiFixtures.singleQuote().reason)
        assertNotNull(NotificationItemUiFixtures.singleQuote().subjectPost)
        assertEquals(NotificationReason.Mention, NotificationItemUiFixtures.singleMention().reason)
        assertNotNull(NotificationItemUiFixtures.singleMention().subjectPost)
    }

    @Test
    fun `every known reason has at least one fixture`() {
        val reasonsWithFixtures =
            setOf(
                NotificationItemUiFixtures.singleLike().reason,
                NotificationItemUiFixtures.singleRepost().reason,
                NotificationItemUiFixtures.singleFollow().reason,
                NotificationItemUiFixtures.singleMention().reason,
                NotificationItemUiFixtures.singleReply().reason,
                NotificationItemUiFixtures.singleQuote().reason,
                NotificationItemUiFixtures.singleStarterpackJoined().reason,
                NotificationItemUiFixtures.singleVerified().reason,
                NotificationItemUiFixtures.singleUnverified().reason,
                NotificationItemUiFixtures.singleLikeViaRepost().reason,
                NotificationItemUiFixtures.singleRepostViaRepost().reason,
                NotificationItemUiFixtures.singleSubscribedPost().reason,
                NotificationItemUiFixtures.singleContactMatch().reason,
            )
        // Excludes Unknown — Unknown is the forward-compat fallback and has no fixture.
        val expected = NotificationReason.entries.toSet() - NotificationReason.Unknown
        assertEquals(expected, reasonsWithFixtures)
    }

    @Test
    fun `aggregatedLikes rejects zero or negative actor counts`() {
        assertThrowsIllegalArgumentException { NotificationItemUiFixtures.aggregatedLikes(actorCount = 0) }
        assertThrowsIllegalArgumentException { NotificationItemUiFixtures.aggregatedLikes(actorCount = -1) }
        // 1 actor would violate the Aggregated invariant — caught by NotificationItemUi.Aggregated's init.
        assertThrowsIllegalArgumentException { NotificationItemUiFixtures.aggregatedLikes(actorCount = 1) }
    }

    private fun assertThrowsIllegalArgumentException(block: () -> Unit) {
        try {
            block()
            error("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
