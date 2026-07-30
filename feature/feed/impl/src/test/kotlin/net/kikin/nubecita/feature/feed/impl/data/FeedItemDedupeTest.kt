package net.kikin.nubecita.feature.feed.impl.data

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class FeedItemDedupeTest {
    @Test
    fun `empty list returns same instance`() {
        val empty = emptyList<FeedItemUi>()
        assertSame(empty, empty.dedupeClusterContext())
    }

    @Test
    fun `list of only Singles is returned unchanged when no clusters present`() {
        val items = listOf(single("a"), single("b"), single("c"))
        val deduped = items.dedupeClusterContext()
        assertEquals(items, deduped)
    }

    @Test
    fun `list of only ReplyClusters is returned unchanged`() {
        val items =
            listOf(
                cluster(rootId = "r1", parentId = "p1", leafId = "l1"),
                cluster(rootId = "r2", parentId = "p2", leafId = "l2"),
            )
        val deduped = items.dedupeClusterContext()
        assertEquals(items, deduped)
    }

    @Test
    fun `Single matching a clusters root id is dropped`() {
        val items =
            listOf(
                single("rootPost"),
                cluster(rootId = "rootPost", parentId = "parentPost", leafId = "leafPost"),
            )
        val deduped = items.dedupeClusterContext()
        assertEquals(1, deduped.size)
        assertTrue(deduped.single() is FeedItemUi.ReplyCluster)
    }

    @Test
    fun `Single matching a clusters parent id is dropped`() {
        val items =
            listOf(
                cluster(rootId = "rootPost", parentId = "parentPost", leafId = "leafPost"),
                single("parentPost"),
            )
        val deduped = items.dedupeClusterContext()
        assertEquals(1, deduped.size)
        assertTrue(deduped.single() is FeedItemUi.ReplyCluster)
    }

    @Test
    fun `dedup is order-independent — Single before or after the cluster is dropped`() {
        val before =
            listOf(
                single("rootPost"),
                cluster(rootId = "rootPost", parentId = "parentPost", leafId = "leafPost"),
            ).dedupeClusterContext()
        val after =
            listOf(
                cluster(rootId = "rootPost", parentId = "parentPost", leafId = "leafPost"),
                single("rootPost"),
            ).dedupeClusterContext()
        assertEquals(1, before.size)
        assertEquals(1, after.size)
    }

    @Test
    fun `Singles unrelated to any cluster are preserved`() {
        val items =
            listOf(
                single("standalone1"),
                cluster(rootId = "rootPost", parentId = "parentPost", leafId = "leafPost"),
                single("standalone2"),
            )
        val deduped = items.dedupeClusterContext()
        assertEquals(3, deduped.size)
    }

    @Test
    fun `direct-reply-to-root cluster (root id == parent id) drops a matching Single once`() {
        // The wire-level shape where replyRef.parent.uri == replyRef.root.uri.
        // Both root and parent register the same URI in the contextUris set;
        // a single matching Single is dropped (idempotent — set semantics).
        val items =
            listOf(
                single("samePost"),
                cluster(rootId = "samePost", parentId = "samePost", leafId = "leafPost"),
            )
        val deduped = items.dedupeClusterContext()
        assertEquals(1, deduped.size)
        assertTrue(deduped.single() is FeedItemUi.ReplyCluster)
    }

    @Test
    fun `dedupeByKey drops duplicate Singles by post id`() {
        // Reproduces nubecita-7p3: same post URI shows up twice in a page
        // because (a) viewer reposted it and (b) someone they follow also
        // reposted it. The LazyColumn's slot key would collide.
        val items = listOf(single("a"), single("b"), single("a"), single("c"))
        val deduped = items.dedupeByKey()
        assertEquals(3, deduped.size)
        assertEquals(listOf("a", "b", "c"), deduped.map { it.key })
    }

    @Test
    fun `dedupeByKey drops a Single whose URI matches a clusters leaf`() {
        // ReplyCluster.key == leaf.id; a later Single(post=leaf) collides
        // on the LazyColumn key and crashes the feed mid-scroll.
        val items =
            listOf(
                cluster(rootId = "rootA", parentId = "parentA", leafId = "leafA"),
                single("leafA"),
            )
        val deduped = items.dedupeByKey()
        assertEquals(1, deduped.size)
        assertTrue(deduped.single() is FeedItemUi.ReplyCluster)
    }

    @Test
    fun `dedupeByKey is a no-op on lists with all-unique keys`() {
        val items = listOf(single("a"), single("b"), single("c"))
        val deduped = items.dedupeByKey()
        assertEquals(items, deduped)
    }

    @Test
    fun `dedupeByKey is a no-op on empty and single-element lists`() {
        assertEquals(emptyList<FeedItemUi>(), emptyList<FeedItemUi>().dedupeByKey())
        val one = listOf(single("only"))
        assertSame(one, one.dedupeByKey())
    }

    @Test
    fun `dedupeByKey keeps the first occurrence`() {
        val first = single("dup")
        val second = single("dup")
        val deduped = listOf(first, single("other"), second).dedupeByKey()
        assertEquals(2, deduped.size)
        assertSame(first, deduped[0])
    }

    // ---------- D5: the disputed ordering (nubecita-w9of) ----------

    /**
     * The ordering `dedupeClusterContext` and thread-root de-duplication
     * disagree about: a `Single` for post P sitting ABOVE a `ReplyCluster`
     * rooted at P.
     *
     * Chronologically this should be impossible — a reply is always newer than
     * its parent, so on a newest-first timeline the cluster precedes the
     * standalone. Measured over 216 live timeline entries, 17 of 18 such pairs
     * had the cluster first. The single violation was a REPOST: a repost's
     * feed position reflects the repost time, not the original post's, so a
     * recently-reposted old post can surface above a reply to it.
     *
     * In that case the repost is both the newer event and an explicit
     * endorsement by someone the viewer follows, so it must survive. Today it
     * does not: `dedupeClusterContext` has no repost exemption and drops any
     * `Single` whose id appears as cluster context, so this reposted post
     * disappears from the feed.
     */
    @Test
    fun `a reposted Single is not dropped when a cluster shares its root`() {
        val repost = FeedItemUi.Single(samplePost("P").copy(repostedBy = "Someone You Follow"))
        val clusterRootedAtP = cluster(rootId = "P", parentId = "P", leafId = "reply")

        val deduped = listOf(repost, clusterRootedAtP).dedupeClusterContext()

        assertTrue(
            deduped.any { it is FeedItemUi.Single && it.post.repostedBy != null },
            "the repost was dropped; an endorsement by a followed account must survive cluster-context dedupe",
        )
    }

    /**
     * The non-repost half of the same shape stays as it is: a plain standalone
     * shadowed by cluster context is still the duplicate, and the cluster is
     * still canonical.
     */
    @Test
    fun `a plain Single shadowed by cluster context is still dropped`() {
        val deduped = listOf(single("P"), cluster(rootId = "P", parentId = "P", leafId = "reply")).dedupeClusterContext()

        assertEquals(1, deduped.size)
        assertTrue(deduped.single() is FeedItemUi.ReplyCluster)
    }

    private fun single(id: String): FeedItemUi.Single = FeedItemUi.Single(samplePost(id))

    private fun cluster(
        rootId: String,
        parentId: String,
        leafId: String,
    ): FeedItemUi.ReplyCluster =
        FeedItemUi.ReplyCluster(
            root = samplePost(rootId),
            parent = samplePost(parentId),
            leaf = samplePost(leafId),
            hasEllipsis = false,
        )

    // ---------- pass ORDER: no post may be silently lost ----------

    /**
     * The two passes are not commutative, and one order loses a post entirely.
     *
     * `L1` is a mid-thread post that reached the timeline as a `Single` — which
     * `FeedViewPostMapper` produces on four fallback paths (blocked parent,
     * not-found root, or either failing moderation projection), so this shape is
     * reachable, not hypothetical.
     *
     * Running `dedupeClusterContext` FIRST drops `Single(L1)` because L1 is the
     * parent of cluster2 — and then `dedupeByThreadRoot` drops cluster2 itself
     * for reusing root R. L1 is now rendered nowhere AND counted nowhere.
     *
     * Running `dedupeByThreadRoot` FIRST removes cluster2 before L1 is anyone's
     * context, so L1 survives as a standalone. The design names silent loss as a
     * strictly worse failure than duplication ("unrelated posts disappear from
     * the feed — a much worse failure than the duplication being fixed"), so the
     * thread-root pass runs first.
     */
    @Test
    fun `a post rendered only as a dropped clusters parent is not lost`() {
        val cluster1 = cluster(rootId = "R", parentId = "R", leafId = "L3")
        val cluster2 = cluster(rootId = "R", parentId = "L1", leafId = "L2")
        val orphan = FeedItemUi.Single(samplePost("L1"))
        val items = listOf(cluster1, cluster2, orphan)

        val deduped = items.dedupeByThreadRoot().dedupeClusterContext().dedupeByKey()

        val rendered = deduped.flatMap { it.renderedIds() }
        assertTrue("L1" in rendered, "L1 vanished from the feed: rendered=$rendered")
    }

    /**
     * The order must not regress the case the cluster-context pass exists for:
     * a standalone post that IS a surviving cluster's context still gets dropped.
     */
    @Test
    fun `a standalone that is a surviving clusters parent is still dropped`() {
        val cluster = cluster(rootId = "R", parentId = "P", leafId = "L")
        val standalone = FeedItemUi.Single(samplePost("P"))

        val deduped = listOf(cluster, standalone).dedupeByThreadRoot().dedupeClusterContext()

        assertEquals(listOf(cluster.key), deduped.map { it.key })
    }

    private fun FeedItemUi.renderedIds(): List<String> =
        when (this) {
            is FeedItemUi.ReplyCluster -> listOf(root.id, parent.id, leaf.id)
            is FeedItemUi.SelfThreadChain -> posts.map { it.id }
            is FeedItemUi.Single -> listOf(post.id)
            is FeedItemUi.Blocked, is FeedItemUi.NotFound -> emptyList()
        }

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
