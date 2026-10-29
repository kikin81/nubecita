package net.kikin.nubecita.feature.notifications.impl.data

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class NotificationsPageMergeTest {
    @Test
    fun `distinct keys are appended in page order`() {
        val existing = persistentListOf(like("a1"), like("a2"))

        val merged = existing.appendPage(listOf(like("a3"), like("a4")))

        assertEquals(listOf("a1", "a2", "a3", "a4"), merged.map { it.itemKey })
    }

    @Test
    fun `empty page returns the existing list unchanged`() {
        val existing = persistentListOf(like("a1"))

        assertSame(existing, existing.appendPage(emptyList()))
    }

    @Test
    fun `aggregate split across a page boundary merges into the existing row`() {
        // The crash: a day's follows straddle the page boundary, so both
        // pages emit "agg:Follow:<day>" and LazyColumn rejects the repeat.
        val newer =
            NotificationItemUiFixtures.aggregatedFollows(
                actorCount = 2,
                itemKey = FOLLOW_KEY,
                isRead = true,
                indexedAt = Instant.parse("2026-10-18T20:00:00Z"),
            )
        val older =
            NotificationItemUi.Aggregated(
                itemKey = FOLLOW_KEY,
                reason = newer.reason,
                indexedAt = Instant.parse("2026-10-18T08:00:00Z"),
                isRead = false,
                actors = persistentListOf(ERIN, FRANK),
                subjectPost = null,
            )
        val existing = persistentListOf(like("a1"), newer)

        val merged = existing.appendPage(listOf(older, like("a3")))

        assertEquals(listOf("a1", FOLLOW_KEY, "a3"), merged.map { it.itemKey })
        val row = merged[1]
        assertTrue(row is NotificationItemUi.Aggregated)
        assertEquals(newer.actors + listOf(ERIN, FRANK), row.actors, "newest-page actors stay first")
        assertEquals(newer.indexedAt, row.indexedAt, "row keeps the newest timestamp")
        assertFalse(row.isRead, "one unread contributor keeps the merged row unread")
    }

    @Test
    fun `single on the existing side absorbs a same-key aggregate from the next page`() {
        val existingSingle = like(FOLLOW_KEY)
        val incoming =
            NotificationItemUiFixtures.aggregatedLikes(
                actorCount = 2,
                itemKey = FOLLOW_KEY,
                indexedAt = Instant.parse("2026-05-25T12:00:00Z"),
            )

        val merged = persistentListOf(existingSingle).appendPage(listOf(incoming))

        val row = merged.single()
        assertTrue(row is NotificationItemUi.Aggregated)
        // Both fixtures lead with Alice, so she is listed once: Alice, Bob.
        assertEquals(incoming.actors, row.actors)
    }

    @Test
    fun `actor present on both sides of a merge is listed once`() {
        val newer = NotificationItemUiFixtures.aggregatedFollows(actorCount = 2, itemKey = FOLLOW_KEY)
        val older =
            NotificationItemUi.Aggregated(
                itemKey = FOLLOW_KEY,
                reason = newer.reason,
                indexedAt = newer.indexedAt,
                isRead = false,
                actors = persistentListOf(newer.actors[1], ERIN),
                subjectPost = null,
            )

        val row = persistentListOf(newer).appendPage(listOf(older)).single()

        assertEquals(newer.actors + ERIN, row.actors)
    }

    @Test
    fun `merge that dedupes down to one actor stays a single`() {
        val existing = NotificationItemUiFixtures.singleLike(itemKey = FOLLOW_KEY)
        val alice = existing.actors.single()
        val incoming =
            NotificationItemUi.Aggregated(
                itemKey = FOLLOW_KEY,
                reason = existing.reason,
                indexedAt = existing.indexedAt,
                isRead = false,
                actors = persistentListOf(alice, alice),
                subjectPost = null,
            )

        val row = persistentListOf(existing).appendPage(listOf(incoming)).single()

        assertTrue(row is NotificationItemUi.Single)
        assertEquals(listOf(alice), row.actors)
    }

    @Test
    fun `repeated single from an overlapping page is dropped`() {
        val first = like("at://did:plc:x/app.bsky.feed.post/1", isRead = true)
        val repeat = like("at://did:plc:x/app.bsky.feed.post/1", isRead = false)

        val merged = persistentListOf(first).appendPage(listOf(repeat, like("a2")))

        assertEquals(listOf(first.itemKey, "a2"), merged.map { it.itemKey })
        assertSame(first, merged[0], "the already-rendered row is kept as-is")
    }

    @Test
    fun `merged output never contains a duplicate key`() {
        val existing = persistentListOf(like("a1"), like("dup"))

        val merged = existing.appendPage(listOf(like("dup"), like("a2"), like("a2")))

        assertEquals(merged.size, merged.map { it.itemKey }.toSet().size)
    }

    private fun like(
        key: String,
        isRead: Boolean = false,
    ): NotificationItemUi.Single = NotificationItemUiFixtures.singleLike(itemKey = key, isRead = isRead)

    private companion object {
        const val FOLLOW_KEY = "agg:Follow:2026-10-18"
        val ERIN = AuthorUi(did = "did:plc:erin", handle = "erin.bsky.social", displayName = "Erin", avatarUrl = null)
        val FRANK = AuthorUi(did = "did:plc:frank", handle = "frank.bsky.social", displayName = "Frank", avatarUrl = null)
    }
}
