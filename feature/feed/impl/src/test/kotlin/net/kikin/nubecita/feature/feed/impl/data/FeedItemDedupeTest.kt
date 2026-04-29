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

    private fun samplePost(id: String): PostUi =
        PostUi(
            id = id,
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
