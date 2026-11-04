package net.kikin.nubecita.core.feedmapping

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

        assertEquals(listOf(first.key), deduped.map { it.key })
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

        assertEquals(listOf(page1.key), deduped.map { it.key })
    }

    @Test
    fun `seven replies into one thread collapse to a single item`() {
        val items = (1..7).map { cluster(rootId = "R", parentId = "R", leafId = "reply$it") }

        val deduped = items.dedupeByThreadRoot()

        assertEquals(1, deduped.size)
        assertEquals(items.first().key, deduped.single().key)
    }

    // ---------- root derivation ----------

    @Test
    fun `a standalone post reserves its own thread root`() {
        val standalone = single("P")
        val laterReply = cluster(rootId = "P", parentId = "P", leafId = "reply")

        val deduped = listOf(standalone, laterReply).dedupeByThreadRoot()

        assertEquals(listOf(standalone.key), deduped.map { it.key })
    }

    @Test
    fun `a self-thread chain reserves the thread of its first post`() {
        val chain = chain("A", "B", "C")
        val laterReply = cluster(rootId = "A", parentId = "A", leafId = "reply")

        val deduped = listOf(chain, laterReply).dedupeByThreadRoot()

        assertEquals(listOf(chain.key), deduped.map { it.key })
    }

    // ---------- repost exemption (design D4) ----------

    @Test
    fun `a repost is never dropped even when its thread root was already seen`() {
        val firstReply = cluster(rootId = "R", parentId = "R", leafId = "reply1")
        val repost = FeedItemUi.Single(samplePost("R").copy(repostedBy = "Someone You Follow"))

        val deduped = listOf(firstReply, repost).dedupeByThreadRoot()

        assertEquals(listOf(firstReply.key, repost.key), deduped.map { it.key })
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

        assertEquals(listOf(repost.key), deduped.map { it.key })
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

        assertEquals(listOf(first.key, unrelated.key), deduped.map { it.key })
    }

    @Test
    fun `an empty list is returned unchanged`() {
        assertTrue(emptyList<FeedItemUi>().dedupeByThreadRoot().isEmpty())
    }

    // ---------- suppressed-reply count (design D6) ----------

    @Test
    fun `the survivor counts the replies that were suppressed`() {
        val items = (1..7).map { cluster(rootId = "R", parentId = "R", leafId = "reply$it") }

        val deduped = items.dedupeByThreadRoot()

        assertEquals(6, deduped.single().suppressedReplyCount)
    }

    @Test
    fun `an item with no suppressed siblings reports zero`() {
        val deduped = listOf(single("a"), cluster(rootId = "r", parentId = "r", leafId = "l")).dedupeByThreadRoot()

        assertTrue(deduped.all { it.suppressedReplyCount == 0 })
    }

    /**
     * Spec edge case 1: the count is in POSTS, not feed items. A dropped
     * `SelfThreadChain` carries three replies, so it contributes three — not
     * one — even though it is a single item.
     */
    @Test
    fun `a dropped self-thread chain contributes each of its posts`() {
        val survivor = cluster(rootId = "R", parentId = "R", leafId = "first")
        val droppedChain = FeedItemUi.SelfThreadChain(posts = listOf(samplePost("R"), samplePost("c2"), samplePost("c3")).toImmutableList())

        val deduped = listOf(survivor, droppedChain).dedupeByThreadRoot()

        // R is already rendered as the survivor's root, so only c2 and c3 are new.
        assertEquals(2, deduped.single().suppressedReplyCount)
    }

    /**
     * Spec edge case 2: a suppressed post already rendered elsewhere is not
     * counted. A dropped `Single` is always the thread root, which the
     * survivor already renders — so it adds nothing.
     */
    @Test
    fun `a dropped standalone already visible as context is not counted`() {
        val survivor = cluster(rootId = "P", parentId = "P", leafId = "reply")
        val droppedStandalone = single("P")

        val deduped = listOf(survivor, droppedStandalone).dedupeByThreadRoot()

        assertEquals(0, deduped.single().suppressedReplyCount)
    }

    /**
     * The live reproduction, which is exactly this case: the dropped cluster's
     * LEAF is the survivor's PARENT, so the viewer can already see it and the
     * count must stay at zero rather than promising a reply they are already
     * looking at.
     */
    @Test
    fun `a dropped cluster whose leaf is the survivors parent is not counted`() {
        val survivor = cluster(rootId = "R", parentId = "P1", leafId = "L1")
        val dropped = cluster(rootId = "R", parentId = "P0", leafId = "P1")

        val deduped = listOf(survivor, dropped).dedupeByThreadRoot()

        assertEquals(0, deduped.single().suppressedReplyCount)
    }

    @Test
    fun `a surviving standalone carries the count for replies it suppressed`() {
        val standalone = single("P")
        val droppedReply = cluster(rootId = "P", parentId = "P", leafId = "reply")

        val deduped = listOf(standalone, droppedReply).dedupeByThreadRoot()

        assertEquals(1, deduped.single().suppressedReplyCount)
    }

    /**
     * A repost survives alongside the first item for the same root. The count
     * must land on ONE of them, not be double-reported on both.
     */
    @Test
    fun `the count is not double-reported when a repost also survives`() {
        val first = cluster(rootId = "R", parentId = "R", leafId = "l1")
        val repost = FeedItemUi.Single(samplePost("R").copy(repostedBy = "Someone"))
        val droppedSibling = cluster(rootId = "R", parentId = "R", leafId = "l2")

        val deduped = listOf(first, repost, droppedSibling).dedupeByThreadRoot()

        assertEquals(1, deduped.sumOf { it.suppressedReplyCount.toLong() })
    }

    // ---------- helpers ----------

    // --- cross-page scoping (design D7) -----------------------------------
    //
    // The seeded parameters are how the three load directions differ. These
    // cases pin the asymmetry itself, because the two directions produce
    // OPPOSITE outcomes from identical input and a single implementation that
    // ignored the seed would still pass every unseeded test above.

    @Test
    fun `append drops a second reply into a thread already rendered above`() {
        // What the accumulated list used to give for free: page 2's reply into a
        // thread page 1 already shows is older than what is on screen.
        val page = listOf(cluster("root-1", "parent-2", "leaf-3"))

        val kept = page.dedupeByThreadRoot(seenRoots = setOf("root-1"))

        assertTrue(kept.isEmpty(), "expected the page-2 duplicate to be dropped, got $kept")
    }

    @Test
    fun `prepend keeps a newer reply into a thread rendered below the seam`() {
        // The same input, the opposite outcome. A prepend seeds nothing, so the
        // thread resurfaces with its newer reply instead of being suppressed by
        // an occurrence the reader has already scrolled past.
        val page = listOf(cluster("root-1", "parent-2", "leaf-3"))

        val kept = page.dedupeByThreadRoot()

        assertEquals(1, kept.size)
        assertEquals("leaf-3", kept.single().leafId)
    }

    @Test
    fun `a single item is still dropped when its root was seen above`() {
        // size < 2 used to be a safe early return; with a seed it is not, and
        // this is the case that would slip through if the shortcut stayed.
        val page = listOf(single("root-1"))

        assertTrue(page.dedupeByThreadRoot(seenRoots = setOf("root-1")).isEmpty())
    }

    @Test
    fun `a repost is still exempt when its root was seen above`() {
        // The repost exemption is not weakened by seeding: an endorsement by
        // someone the viewer follows is its own event.
        val page = listOf(repost("root-1"))

        assertEquals(1, page.dedupeByThreadRoot(seenRoots = setOf("root-1")).size)
    }

    @Test
    fun `duplicates within one written page are dropped regardless of the seed`() {
        val page =
            listOf(
                cluster("root-9", "parent-1", "leaf-1"),
                cluster("root-9", "parent-2", "leaf-2"),
            )

        val kept = page.dedupeByThreadRoot(seenRoots = setOf("root-other"))

        assertEquals(1, kept.size)
        assertEquals("leaf-1", kept.single().leafId)
    }

    @Test
    fun `an empty seed reproduces the unseeded behaviour exactly`() {
        // The compatibility guarantee the default arguments rest on.
        val page =
            listOf(
                single("a"),
                cluster("root-9", "parent-1", "leaf-1"),
                cluster("root-9", "parent-2", "leaf-2"),
            )

        assertEquals(
            page.dedupeByThreadRoot().map { it.key },
            page.dedupeByThreadRoot(seenRoots = emptySet(), visibleFromAbove = emptySet()).map { it.key },
        )
    }

    @Test
    fun `a post already visible above is not counted as a suppressed reply`() {
        // visibleFromAbove feeds the same rule the unseeded pass applies within
        // a page: a post the reader can already see is not "more replies".
        val page =
            listOf(
                cluster("root-5", "parent-5", "leaf-5"),
                cluster("root-5", "parent-6", "leaf-6"),
            )

        val withoutContext = page.dedupeByThreadRoot().single()
        val withContext =
            page.dedupeByThreadRoot(visibleFromAbove = setOf("leaf-6", "parent-6")).single()

        assertTrue(
            withContext.suppressedReplyCount <= withoutContext.suppressedReplyCount,
            "seeding visible posts must not increase the suppressed count",
        )
    }

    private fun repost(id: String) = FeedItemUi.Single(samplePost(id).copy(repostedBy = "Reposter"))

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
