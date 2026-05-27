package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class NotificationItemUiTest {
    private val author =
        AuthorUi(
            did = "did:plc:test0000000000000000000000",
            handle = "test.bsky.social",
            displayName = "Test",
            avatarUrl = null,
        )

    private val indexedAt = Instant.parse("2026-05-26T12:00:00Z")

    @Test
    fun `Single requires exactly one actor`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                NotificationItemUi.Single(
                    itemKey = "k",
                    reason = NotificationReason.Like,
                    indexedAt = indexedAt,
                    isRead = false,
                    actors = persistentListOf(author, author),
                    subjectPost = null,
                )
            }
        assertEquals(
            "NotificationItemUi.Single requires exactly one actor; got 2",
            ex.message,
        )
    }

    @Test
    fun `Single rejects empty actor list`() {
        assertThrows(IllegalArgumentException::class.java) {
            NotificationItemUi.Single(
                itemKey = "k",
                reason = NotificationReason.Like,
                indexedAt = indexedAt,
                isRead = false,
                actors = persistentListOf(),
                subjectPost = null,
            )
        }
    }

    @Test
    fun `Aggregated requires two or more actors`() {
        val ex =
            assertThrows(IllegalArgumentException::class.java) {
                NotificationItemUi.Aggregated(
                    itemKey = "k",
                    reason = NotificationReason.Like,
                    indexedAt = indexedAt,
                    isRead = false,
                    actors = persistentListOf(author),
                    subjectPost = null,
                )
            }
        assertEquals(
            "NotificationItemUi.Aggregated requires two or more actors; got 1",
            ex.message,
        )
    }
}
