package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
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
    fun `ReplyCluster key namespaces the leaf, and leafId exposes it bare`() {
        val root = PostUiFixtures.fakePost(id = "at://did:plc:tom/app.bsky.feed.post/root")
        val parent = PostUiFixtures.fakePost(id = "at://did:plc:nolram/app.bsky.feed.post/parent")
        val leaf = PostUiFixtures.fakePost(id = "at://did:plc:romain/app.bsky.feed.post/leaf")
        val cluster: FeedItemUi = FeedItemUi.ReplyCluster(root = root, parent = parent, leaf = leaf, hasEllipsis = false)
        assertEquals("cluster:at://did:plc:romain/app.bsky.feed.post/leaf", cluster.key)
        assertEquals("at://did:plc:romain/app.bsky.feed.post/leaf", cluster.leafId)
    }

    @Test
    fun `two replies into one thread do not share a key`() {
        // The crash D28 corrects. Keyed on the ROOT, these two clusters would
        // be identical — and D7 lets exactly this pair coexist, a newer reply
        // prepended above an older one still rendered below the seam. A
        // duplicate LazyColumn key throws, and the anchor's item_key lookup
        // would match two rows.
        val root = PostUiFixtures.fakePost(id = "at://thread/root")
        val older =
            FeedItemUi.ReplyCluster(
                root = root,
                parent = PostUiFixtures.fakePost(id = "at://thread/p1"),
                leaf = PostUiFixtures.fakePost(id = "at://thread/reply-1"),
                hasEllipsis = false,
            )
        val newer =
            FeedItemUi.ReplyCluster(
                root = root,
                parent = PostUiFixtures.fakePost(id = "at://thread/p2"),
                leaf = PostUiFixtures.fakePost(id = "at://thread/reply-2"),
                hasEllipsis = false,
            )

        assertNotEquals(older.key, newer.key)
    }

    @Test
    fun `a chain's key survives the chain gaining a post`() {
        // The reason the key anchors on the first post (D12): a chain can gain
        // a reply between sessions, and an anchor keyed on its leaf would
        // resolve to nothing the next time the reader opens the app.
        val first = PostUiFixtures.fakePost(id = "at://chain/1")
        val second = PostUiFixtures.fakePost(id = "at://chain/2")
        val third = PostUiFixtures.fakePost(id = "at://chain/3")
        val before = FeedItemUi.SelfThreadChain(persistentListOf(first, second))
        val after = FeedItemUi.SelfThreadChain(persistentListOf(first, second, third))

        assertEquals(before.key, after.key)
        assertEquals("chain:at://chain/1", after.key)
        // The timeline slot moved, though, and that is what a deletion acts on.
        assertEquals("at://chain/2", before.leafId)
        assertEquals("at://chain/3", after.leafId)
    }

    @Test
    fun `keys are unique across a mixed list that shares posts`() {
        // A chain rooted at P and a Single rendering P: keyed on the bare post
        // id they would collide, which is what the namespace is for. The
        // collision used to be unreachable only because of an invariant in
        // another module, and D7 made that invariant per-page.
        val shared = PostUiFixtures.fakePost(id = "at://shared/post")
        val items: List<FeedItemUi> =
            listOf(
                FeedItemUi.Single(shared),
                FeedItemUi.SelfThreadChain(persistentListOf(shared, PostUiFixtures.fakePost(id = "at://shared/next"))),
                FeedItemUi.ReplyCluster(
                    root = shared,
                    parent = PostUiFixtures.fakePost(id = "at://other/parent"),
                    leaf = PostUiFixtures.fakePost(id = "at://other/leaf"),
                    hasEllipsis = false,
                ),
                FeedItemUi.Blocked(uri = "at://shared/post", authorDid = "did:plc:x"),
                FeedItemUi.NotFound(uri = "at://shared/post"),
            )

        assertEquals(items.size, items.map { it.key }.toSet().size)
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
    fun `Blocked key encodes the variant role alongside uri`() {
        val item: FeedItemUi =
            FeedItemUi.Blocked(
                uri = "at://did:plc:blocked/app.bsky.feed.post/x",
                authorDid = "did:plc:blocked",
            )
        // The "blocked:" prefix keeps the LazyColumn key disjoint from
        // any Single carrying the same URI — a post can transition from
        // Single → Blocked when the viewer blocks the author, and the
        // recycler must treat the two as distinct slots.
        assertEquals("blocked:at://did:plc:blocked/app.bsky.feed.post/x", item.key)
    }

    @Test
    fun `NotFound key encodes the variant role alongside uri`() {
        val item: FeedItemUi = FeedItemUi.NotFound(uri = "at://did:plc:abc/app.bsky.feed.post/y")
        assertEquals("notfound:at://did:plc:abc/app.bsky.feed.post/y", item.key)
    }

    @Test
    fun `All FeedItemUi variants are exhaustive when targets`() {
        // The compiler verifies exhaustiveness; this test exists so a
        // future variant addition breaks this compile and forces the
        // implementer to update render dispatch sites everywhere.
        // (Updated when SelfThreadChain landed in m28.4 — the test
        // tripwire fired exactly as designed. Updated again when
        // Blocked + NotFound landed in oftc.6.)
        val item: FeedItemUi = FeedItemUi.Single(PostUiFixtures.fakePost())
        val rendered: String =
            when (item) {
                is FeedItemUi.Single -> "single"
                is FeedItemUi.ReplyCluster -> "cluster"
                is FeedItemUi.SelfThreadChain -> "chain"
                is FeedItemUi.Blocked -> "blocked"
                is FeedItemUi.NotFound -> "notfound"
            }
        assertEquals("single", rendered)
    }
}
