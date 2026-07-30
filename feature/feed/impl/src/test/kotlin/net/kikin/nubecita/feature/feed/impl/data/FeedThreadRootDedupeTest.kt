package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Tests for `dedupeByThreadRoot` — the thread-root pass from
 * `openspec/changes/fix-feed-thread-root-dedupe`.
 *
 * The bug: the timeline returns entries in post time order, so several replies
 * into the same thread each become their own `ReplyCluster` and each re-renders
 * that thread's root as context. Measured on a production account, 6 of 180
 * thread roots were duplicated this way, the worst with 7 replies across 3
 * pages.
 *
 * The rule (design D1/D2): keep the FIRST item per thread root. On a
 * newest-first timeline that is always the newest reply, so every dropped
 * sibling is strictly older.
 */
internal class FeedThreadRootDedupeTest {
    // ---------- the reported bug ----------

    @Test
    fun `two replies into the same thread collapse to the first`() {
        val first = cluster(rootId = "R", parentId = "R", leafId = "reply1")
        val second = cluster(rootId = "R", parentId = "reply1", leafId = "reply2")

        val deduped = listOf(first, second).dedupeByThreadRoot()

        assertEquals(listOf(first), deduped)
    }

    /**
     * The live reproduction: two entries one page apart sharing root
     * `3mrulolaklc2m`, where the second cluster's leaf is the first cluster's
     * parent. Applied to the accumulated list, which is how the VM runs it.
     */
    @Test
    fun `replies spanning two pages collapse to the newer one`() {
        val page1 = cluster(rootId = "R", parentId = "P1", leafId = "L1")
        val page2 = cluster(rootId = "R", parentId = "P0", leafId = "P1")

        val deduped = (listOf(page1) + listOf(page2)).dedupeByThreadRoot()

        assertEquals(listOf(page1), deduped)
    }

    @Test
    fun `seven replies into one thread collapse to a single item`() {
        val items = (1..7).map { cluster(rootId = "R", parentId = "R", leafId = "reply$it") }

        val deduped = items.dedupeByThreadRoot()

        assertEquals(1, deduped.size)
        assertEquals(items.first(), deduped.single())
    }

    // ---------- root derivation ----------

    @Test
    fun `a standalone post reserves its own thread root`() {
        val standalone = single("P")
        val laterReply = cluster(rootId = "P", parentId = "P", leafId = "reply")

        val deduped = listOf(standalone, laterReply).dedupeByThreadRoot()

        assertEquals(listOf(standalone), deduped)
    }

    @Test
    fun `a self-thread chain reserves the thread of its first post`() {
        val chain = chain("A", "B", "C")
        val laterReply = cluster(rootId = "A", parentId = "A", leafId = "reply")

        val deduped = listOf(chain, laterReply).dedupeByThreadRoot()

        assertEquals(listOf(chain), deduped)
    }

    // ---------- repost exemption (design D4) ----------

    @Test
    fun `a repost is never dropped even when its thread root was already seen`() {
        val firstReply = cluster(rootId = "R", parentId = "R", leafId = "reply1")
        val repost = FeedItemUi.Single(samplePost("R").copy(repostedBy = "Someone You Follow"))

        val deduped = listOf(firstReply, repost).dedupeByThreadRoot()

        assertEquals(listOf(firstReply, repost), deduped)
    }

    /**
     * A repost still REGISTERS its root, so a later plain reply into the same
     * thread does not stack on top of it.
     */
    @Test
    fun `a repost registers its thread root for later items`() {
        val repost = FeedItemUi.Single(samplePost("R").copy(repostedBy = "Someone You Follow"))
        val laterReply = cluster(rootId = "R", parentId = "R", leafId = "reply")

        val deduped = listOf(repost, laterReply).dedupeByThreadRoot()

        assertEquals(listOf(repost), deduped)
    }

    // ---------- tombstones ----------

    @Test
    fun `tombstones are never dropped`() {
        val items = listOf(FeedItemUi.Blocked("b1", authorDid = "did:plc:b1"), FeedItemUi.NotFound("n1"), FeedItemUi.Blocked("b2", authorDid = "did:plc:b2"))

        val deduped = items.dedupeByThreadRoot()

        assertEquals(items, deduped)
    }

    // ---------- the negative case that matters most (task 2.5) ----------

    /**
     * The failure mode worse than the bug: if root derivation is wrong,
     * unrelated posts vanish from the feed. This mirrors the real sample shape
     * — mostly standalone posts with a few distinct-root replies — and asserts
     * that NOTHING is dropped when no thread root repeats.
     */
    @Test
    fun `nothing is dropped when every thread root is distinct`() {
        val items =
            listOf(
                single("a"),
                single("b"),
                cluster(rootId = "r1", parentId = "r1", leafId = "l1"),
                single("c"),
                chain("d1", "d2"),
                cluster(rootId = "r2", parentId = "r2", leafId = "l2"),
                FeedItemUi.Blocked("blocked", authorDid = "did:plc:blocked"),
                single("e"),
            )

        val deduped = items.dedupeByThreadRoot()

        assertEquals(items, deduped)
    }

    @Test
    fun `an unrelated standalone between two siblings survives`() {
        val first = cluster(rootId = "R", parentId = "R", leafId = "l1")
        val unrelated = single("unrelated")
        val sibling = cluster(rootId = "R", parentId = "R", leafId = "l2")

        val deduped = listOf(first, unrelated, sibling).dedupeByThreadRoot()

        assertEquals(listOf(first, unrelated), deduped)
    }

    @Test
    fun `an empty list is returned unchanged`() {
        assertTrue(emptyList<FeedItemUi>().dedupeByThreadRoot().isEmpty())
    }

    // ---------- helpers ----------

    private fun single(id: String) = FeedItemUi.Single(samplePost(id))

    private fun cluster(
        rootId: String,
        parentId: String,
        leafId: String,
    ) = FeedItemUi.ReplyCluster(
        root = samplePost(rootId),
        parent = samplePost(parentId),
        leaf = samplePost(leafId),
        hasEllipsis = false,
    )

    private fun chain(vararg ids: String) = FeedItemUi.SelfThreadChain(posts = ids.map(::samplePost).toImmutableList())

    private fun samplePost(id: String): PostUi =
        PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author =
                AuthorUi(
                    did = "did:plc:$id",
                    handle = "$id.bsky.social",
                    displayName = id,
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-29T00:00:00Z"),
            text = "post $id",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
