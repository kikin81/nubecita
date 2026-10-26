package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.MessageUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class WearDmSnapshotBuilderTest {
    private fun direct(
        id: String,
        minutesAgo: Long,
        unread: Int = 0,
        muted: Boolean = false,
        request: Boolean = false,
        name: String? = "Alice",
    ) = ConvoRowUi.Direct(
        convoId = id,
        otherUserDid = "did:plc:alice",
        otherUserHandle = "alice.bsky.social",
        displayName = name,
        avatarUrl = null,
        lastMessageSnippet = null,
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        sentAt = Instant.fromEpochMilliseconds(1_000_000L - minutesAgo * 60_000L),
        unreadCount = unread,
        muted = muted,
        isRequest = request,
    )

    private fun message(
        id: String,
        sender: String,
        deleted: Boolean = false,
    ) = MessageUi(id = id, senderDid = sender, isOutgoing = sender == "me", text = "t $id", isDeleted = deleted, sentAt = Instant.fromEpochMilliseconds(5))

    @Test
    fun forWatch_dropsRequests_sortsByRecency_andCapsAtTen() {
        val convos = (1..12).map { direct("c$it", minutesAgo = it.toLong()) } + direct("req", minutesAgo = 0, request = true)

        val shown = convos.shuffled().forWatch()

        assertEquals((1..10).map { "c$it" }, shown.map { it.convoId })
    }

    @Test
    fun groupMessages_carryTheSendersName_andDeletedOnesNoText() {
        val group =
            ConvoRowUi.Group(
                convoId = "g1",
                name = "Weekend Trip",
                members = persistentListOf(AuthorUi(did = "did:plc:bob", handle = "bob.bsky.social", displayName = "Bob", avatarUrl = null)),
                lastMessageSnippet = null,
                lastMessageFromViewer = false,
                lastMessageIsAttachment = false,
                sentAt = null,
            )

        val snapshot =
            buildWearDmSnapshot(
                accountDid = "did:plc:me",
                nowMs = 9,
                shown = listOf(group),
                fetched = mapOf("g1" to listOf(message("m2", "did:plc:bob", deleted = true), message("m1", "me"))),
                previous = null,
            )

        val convo = snapshot.convos.single()
        assertEquals("Weekend Trip", convo.title)
        assertTrue(convo.isGroup)
        val (mine, bobs) = convo.messages
        assertNull(mine.senderName)
        assertEquals("Bob", bobs.senderName)
        assertTrue(bobs.isDeleted)
        assertEquals("", bobs.text)
    }

    @Test
    fun mutedConvos_dontCountTowardTheUnreadTotal_andDirectTitlesFallBackToTheHandle() {
        val snapshot =
            buildWearDmSnapshot(
                accountDid = "did:plc:me",
                nowMs = 9,
                shown = listOf(direct("c1", 1, unread = 2), direct("c2", 2, unread = 5, muted = true, name = " ")),
                fetched = emptyMap(),
                previous = null,
            )

        assertEquals(2, snapshot.unreadCount)
        assertEquals("alice.bsky.social", snapshot.convos[1].title)
    }
}
