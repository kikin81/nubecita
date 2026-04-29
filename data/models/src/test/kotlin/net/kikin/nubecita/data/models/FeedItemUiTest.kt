package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FeedItemUi] — verifies the sealed-type contract,
 * the `key` extension's stable-identifier semantics, and data-class
 * equality on each variant.
 */
class FeedItemUiTest {
    @Test
    fun `Single key is post id`() {
        val post = PostUiFixtures.fakePost(id = "at://did:plc:abc/app.bsky.feed.post/leaf")
        val item: FeedItemUi = FeedItemUi.Single(post)
        assertEquals("at://did:plc:abc/app.bsky.feed.post/leaf", item.key)
    }

    @Test
    fun `ReplyCluster key is leaf id, not root or parent id`() {
        val root = PostUiFixtures.fakePost(id = "at://did:plc:tom/app.bsky.feed.post/root")
        val parent = PostUiFixtures.fakePost(id = "at://did:plc:nolram/app.bsky.feed.post/parent")
        val leaf = PostUiFixtures.fakePost(id = "at://did:plc:romain/app.bsky.feed.post/leaf")
        val cluster: FeedItemUi = FeedItemUi.ReplyCluster(root = root, parent = parent, leaf = leaf, hasEllipsis = false)
        assertEquals("at://did:plc:romain/app.bsky.feed.post/leaf", cluster.key)
    }

    @Test
    fun `Single equality is data-class structural`() {
        val a = FeedItemUi.Single(PostUiFixtures.fakePost(id = "p1"))
        val b = FeedItemUi.Single(PostUiFixtures.fakePost(id = "p1"))
        val c = FeedItemUi.Single(PostUiFixtures.fakePost(id = "p2"))
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `ReplyCluster equality is data-class structural and reacts to hasEllipsis flip`() {
        val root = PostUiFixtures.fakePost(id = "root")
        val parent = PostUiFixtures.fakePost(id = "parent")
        val leaf = PostUiFixtures.fakePost(id = "leaf")
        val withEllipsis =
            FeedItemUi.ReplyCluster(root = root, parent = parent, leaf = leaf, hasEllipsis = true)
        val withoutEllipsis =
            FeedItemUi.ReplyCluster(root = root, parent = parent, leaf = leaf, hasEllipsis = false)
        val withEllipsisDup =
            FeedItemUi.ReplyCluster(root = root, parent = parent, leaf = leaf, hasEllipsis = true)
        assertEquals(withEllipsis, withEllipsisDup)
        assertNotEquals(withEllipsis, withoutEllipsis)
    }

    @Test
    fun `Single and ReplyCluster are exhaustive when targets`() {
        // The compiler verifies exhaustiveness; this test exists so a
        // future variant addition (e.g. SelfThreadChain) breaks this
        // compile and forces the implementer to update render
        // dispatch sites everywhere.
        val item: FeedItemUi = FeedItemUi.Single(PostUiFixtures.fakePost())
        val rendered: String =
            when (item) {
                is FeedItemUi.Single -> "single"
                is FeedItemUi.ReplyCluster -> "cluster"
            }
        assertEquals("single", rendered)
    }
}
