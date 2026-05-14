package net.kikin.nubecita.feature.chats.impl.data

import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.ThreadItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.time.Instant

internal class ThreadItemMapperTest {
    private val viewer = "did:plc:viewer"
    private val peer = "did:plc:alice"
    private val nowLocal = Instant.parse("2026-05-14T18:00:00Z")
    private val laZone = ZoneId.of("America/Los_Angeles")

    @Test
    fun `single message - run of 1, runIndex 0, avatar on incoming`() {
        val items = listOf(msg("a", peer, "2026-05-14T17:30:00Z")).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(1, msgs.size)
        assertEquals(0, msgs[0].runIndex)
        assertEquals(1, msgs[0].runCount)
        assertEquals(true, msgs[0].showAvatar)
    }

    @Test
    fun `single outgoing message - showAvatar false`() {
        val items = listOf(msg("a", viewer, "2026-05-14T17:30:00Z")).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(false, msgs[0].showAvatar)
    }

    @Test
    fun `two same-sender messages - run of 2, oldest is runIndex 0`() {
        // Source is newest-first; input newer→older
        val items =
            listOf(
                msg("newer", peer, "2026-05-14T17:31:00Z"),
                msg("older", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(2, msgs.size)
        // After mapping, the message with id "older" gets runIndex 0; "newer" gets runIndex 1.
        val older = msgs.first { it.message.id == "older" }
        val newer = msgs.first { it.message.id == "newer" }
        assertEquals(0, older.runIndex)
        assertEquals(1, newer.runIndex)
        assertEquals(2, older.runCount)
        assertEquals(2, newer.runCount)
        assertEquals(true, older.showAvatar)
        assertEquals(false, newer.showAvatar)
    }

    @Test
    fun `three same-sender incoming - run of 3, avatar only on oldest`() {
        val items =
            listOf(
                msg("c", peer, "2026-05-14T17:32:00Z"),
                msg("b", peer, "2026-05-14T17:31:00Z"),
                msg("a", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>().associateBy { it.message.id }
        assertEquals(0, msgs.getValue("a").runIndex)
        assertEquals(1, msgs.getValue("b").runIndex)
        assertEquals(2, msgs.getValue("c").runIndex)
        assertEquals(listOf(3, 3, 3), msgs.values.map { it.runCount })
        assertEquals(true, msgs.getValue("a").showAvatar)
        assertEquals(false, msgs.getValue("b").showAvatar)
        assertEquals(false, msgs.getValue("c").showAvatar)
    }

    @Test
    fun `cross-sender alternation - each is its own run`() {
        val items =
            listOf(
                msg("c", peer, "2026-05-14T17:32:00Z"),
                msg("b", viewer, "2026-05-14T17:31:00Z"),
                msg("a", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(3, msgs.size)
        msgs.forEach {
            assertEquals(0, it.runIndex)
            assertEquals(1, it.runCount)
        }
    }

    @Test
    fun `day boundary breaks same-sender run`() {
        // Two peer messages straddling local midnight in LA (UTC-7)
        // 2026-05-14 06:59 UTC = 2026-05-13 23:59 LA
        // 2026-05-14 08:00 UTC = 2026-05-14 01:00 LA
        val items =
            listOf(
                msg("after-midnight", peer, "2026-05-14T08:00:00Z"),
                msg("before-midnight", peer, "2026-05-14T06:59:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        val seps = items.filterIsInstance<ThreadItem.DaySeparator>()
        assertEquals(2, msgs.size)
        assertTrue(seps.isNotEmpty(), "expected a DaySeparator inserted between days")
        // Both should be runs of 1 (separator breaks the run)
        msgs.forEach {
            assertEquals(0, it.runIndex)
            assertEquals(1, it.runCount)
        }
    }

    @Test
    fun `UTC-vs-local timezone regression - California 4pm same day`() {
        // 23:30 UTC vs 00:30 UTC next day in LA = both same LA day (LA UTC-7)
        // 2026-04-25T23:30Z = 2026-04-25 16:30 LA
        // 2026-04-26T00:30Z = 2026-04-25 17:30 LA  (still same LA day!)
        val items =
            listOf(
                msg("late", peer, "2026-04-26T00:30:00Z"),
                msg("early", peer, "2026-04-25T23:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        // BOTH should be in the SAME run (same LA day, same sender, no separator between)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        val seps = items.filterIsInstance<ThreadItem.DaySeparator>()
        assertEquals(2, msgs.size)
        // Exactly one separator at the top (for the day these messages belong to); none BETWEEN them.
        val sepCount = seps.size
        assertEquals(1, sepCount, "exactly one day-separator chip at the top of the (single) run; none between")
    }

    @Test
    fun `day label - today maps to Today literal`() {
        val items = listOf(msg("a", peer, "2026-05-14T17:00:00Z")).toThreadItems(nowLocal, laZone)
        val sep = items.filterIsInstance<ThreadItem.DaySeparator>().firstOrNull()
        assertEquals("Today", sep?.label)
    }

    @Test
    fun `day label - yesterday maps to Yesterday literal`() {
        val items = listOf(msg("a", peer, "2026-05-13T20:00:00Z")).toThreadItems(nowLocal, laZone)
        val sep = items.filterIsInstance<ThreadItem.DaySeparator>().firstOrNull()
        assertEquals("Yesterday", sep?.label)
    }

    @Test
    fun `empty input yields empty output`() {
        val items = emptyList<MessageUi>().toThreadItems(nowLocal, laZone)
        assertEquals(0, items.size)
    }

    @Test
    fun `same label across different days produces different keys`() {
        // LazyColumn keys must be unique. Two chips that happen to share a label
        // (e.g. two distinct Mondays both render as "Mon" inside the 7-day weekday
        // window) MUST still produce distinct keys — otherwise Compose recycles
        // them as the same item. Direct construction is the cleanest way to pin
        // this invariant: same label, different epochDay → different key.
        val a = ThreadItem.DaySeparator(epochDay = 20_000L, label = "Mon")
        val b = ThreadItem.DaySeparator(epochDay = 20_007L, label = "Mon")
        assertTrue(a.key != b.key, "chips for different days must have different LazyColumn keys")
    }

    @Test
    fun `day-separator is emitted AFTER its bucket's messages in source order`() {
        // Under LazyColumn(reverseLayout = true), source[lastIndex] renders at the
        // SCREEN-TOP. The day chip must therefore be at HIGHER source index than
        // its bucket's items so it visually sits ABOVE the day's messages
        // (matching the Bluesky / iMessage / Google Chat convention).
        val items =
            listOf(
                msg("c", peer, "2026-05-14T17:32:00Z"),
                msg("b", peer, "2026-05-14T17:31:00Z"),
                msg("a", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        // Expected layout: [Message c, Message b, Message a, DaySeparator]
        assertEquals(4, items.size)
        assertTrue(items[0] is ThreadItem.Message)
        assertTrue(items[1] is ThreadItem.Message)
        assertTrue(items[2] is ThreadItem.Message)
        assertTrue(items[3] is ThreadItem.DaySeparator, "chip must be the last item (highest source index = screen-top with reverseLayout)")
    }

    @Test
    fun `multi-day - each day chip lands AFTER its bucket's messages`() {
        // Two days, each with one peer message. Source-order expectation:
        //   [today-msg, today-chip, yesterday-msg, yesterday-chip]
        // On screen (reverseLayout): yesterday-chip at top, yesterday-msg below,
        // today-chip below that, today-msg at the bottom.
        val items =
            listOf(
                msg("today", peer, "2026-05-14T17:30:00Z"),
                msg("yesterday", peer, "2026-05-13T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        assertEquals(4, items.size)
        assertTrue(items[0] is ThreadItem.Message && (items[0] as ThreadItem.Message).message.id == "today")
        assertTrue(items[1] is ThreadItem.DaySeparator && (items[1] as ThreadItem.DaySeparator).label == "Today")
        assertTrue(items[2] is ThreadItem.Message && (items[2] as ThreadItem.Message).message.id == "yesterday")
        assertTrue(items[3] is ThreadItem.DaySeparator && (items[3] as ThreadItem.DaySeparator).label == "Yesterday")
    }

    private fun msg(
        id: String,
        senderDid: String,
        sentAt: String,
    ): MessageUi =
        MessageUi(
            id = id,
            senderDid = senderDid,
            isOutgoing = senderDid == viewer,
            text = "msg-$id",
            isDeleted = false,
            sentAt = Instant.parse(sentAt),
        )
}
