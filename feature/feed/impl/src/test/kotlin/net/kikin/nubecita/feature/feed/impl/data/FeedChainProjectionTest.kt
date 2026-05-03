package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the page-level chain projection in
 * `:feature:feed:impl/data/FeedViewPostMapper.toFeedItemsUi()`.
 *
 * These tests verify the strict link rule from the
 * `add-feed-same-author-thread-chain` change's `feature-feed`
 * capability spec: two consecutive feed entries link iff
 * (1) next.reply != null,
 * (2) next.reply.parent is a PostView,
 * (3) parent.author.did == post.author.did,
 * (4) parent.uri == prev.post.uri,
 * (5) neither side has ReasonRepost.
 *
 * Per-entry projection (Single / ReplyCluster paths) is exercised
 * exhaustively by FeedViewPostMapperTest. This file only covers the
 * top-level chain-collapsing pass.
 */
internal class FeedChainProjectionTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `three consecutive same-author self-replies project to one SelfThreadChain`() {
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                ),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/3",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/2", "did:plc:alice000000000000000000"),
                ),
            )

        val items = response.feed.toFeedItemsUi()

        assertEquals(1, items.size)
        val chain = assertInstanceOf(FeedItemUi.SelfThreadChain::class.java, items[0])
        assertEquals(3, chain.posts.size)
        assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", chain.posts[0].id)
        assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/2", chain.posts[1].id)
        assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/3", chain.posts[2].id)
        // Leaf-anchored key matches the last post's URI.
        assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/3", chain.key)
    }

    @Test
    fun `two consecutive same-author self-replies project to one SelfThreadChain`() {
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                ),
            )

        val items = response.feed.toFeedItemsUi()

        assertEquals(1, items.size)
        val chain = assertInstanceOf(FeedItemUi.SelfThreadChain::class.java, items[0])
        assertEquals(2, chain.posts.size)
    }

    @Test
    fun `cross-author entry between same-author posts breaks the chain`() {
        // [A.post1, A.reply2, B.replyX, A.reply3] — A.reply3.reply.parent.uri
        // does NOT match B.replyX.uri, so the link rule rejects.
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                ),
                feedEntry(
                    uri = "at://did:plc:bob00000000000000000000/app.bsky.feed.post/x",
                    authorDid = "did:plc:bob00000000000000000000",
                    replyParent = ReplyParent("at://did:plc:bob00000000000000000000/app.bsky.feed.post/0", "did:plc:bob00000000000000000000"),
                ),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/3",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/2", "did:plc:alice000000000000000000"),
                ),
            )

        val items = response.feed.toFeedItemsUi()

        assertEquals(3, items.size)
        // [SelfThreadChain([alice/1, alice/2]), ReplyCluster(bob/x), ReplyCluster(alice/3)]
        val chain = assertInstanceOf(FeedItemUi.SelfThreadChain::class.java, items[0])
        assertEquals(2, chain.posts.size)
        // Bob's reply projects to a ReplyCluster (parent is a real PostView via the inline ref).
        assertInstanceOf(FeedItemUi.ReplyCluster::class.java, items[1])
        // The orphan Alice reply.3 falls back to its per-entry projection
        // (ReplyCluster — its reply.parent is a real PostView). The chain
        // detection only collapses adjacent same-author runs; orphans
        // surface unchanged.
        val orphan = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, items[2])
        assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/3", orphan.leaf.id)
    }

    @Test
    fun `reposted entry on the next side breaks the chain`() {
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                    reposterHandle = "did:plc:reposter",
                ),
            )

        val items = response.feed.toFeedItemsUi()

        // The reposted entry can't be a chain link → no SelfThreadChain
        // forms. Each entry surfaces via its per-entry projection: post1
        // is a Single (no reply), post2 is a ReplyCluster (its
        // reply.parent is a real PostView and the repost adds repostedBy
        // to the leaf, but the chain rule rejects it).
        assertEquals(2, items.size)
        assertTrue(items.none { it is FeedItemUi.SelfThreadChain })
        assertInstanceOf(FeedItemUi.Single::class.java, items[0])
        assertInstanceOf(FeedItemUi.ReplyCluster::class.java, items[1])
    }

    @Test
    fun `reposted entry on the prev side breaks the chain`() {
        val response =
            decodeTimeline(
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1",
                    authorDid = "did:plc:alice000000000000000000",
                    reposterHandle = "did:plc:reposter",
                ),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                ),
            )

        val items = response.feed.toFeedItemsUi()

        // Same as the symmetric test above — neither chain'd. post1 is
        // a reposted Single (no reply, with repostedBy); post2 is a
        // ReplyCluster (real PostView parent).
        assertEquals(2, items.size)
        assertTrue(items.none { it is FeedItemUi.SelfThreadChain })
        assertInstanceOf(FeedItemUi.Single::class.java, items[0])
        assertInstanceOf(FeedItemUi.ReplyCluster::class.java, items[1])
    }

    @Test
    fun `same-author skip-ahead replies do not chain (parent uri must match prev uri)`() {
        // post3.reply.parent.uri == post1.uri (skipping post2 in the wire) —
        // strict rule rejects skip-ahead chains.
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/3",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                ),
            )

        val items = response.feed.toFeedItemsUi()

        // post1 + post2 are non-replies (Singles); post3 has a real reply
        // ref (parent = post1) so it projects as a ReplyCluster. Skip-
        // ahead chain detection is rejected — none of the three forms a
        // chain link.
        assertEquals(3, items.size)
        assertInstanceOf(FeedItemUi.Single::class.java, items[0])
        assertInstanceOf(FeedItemUi.Single::class.java, items[1])
        assertInstanceOf(FeedItemUi.ReplyCluster::class.java, items[2])
        // None collapsed into a SelfThreadChain.
        assertTrue(items.none { it is FeedItemUi.SelfThreadChain })
    }

    @Test
    fun `parent author DID mismatch breaks the chain`() {
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    // post.author = alice, but reply.parent.author = bob —
                    // not a same-author self-reply.
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:bob00000000000000000000"),
                ),
            )

        val items = response.feed.toFeedItemsUi()

        assertEquals(2, items.size)
        assertTrue(items.all { it is FeedItemUi.Single || it is FeedItemUi.ReplyCluster })
    }

    @Test
    fun `non-chain entries flow through the per-entry projection unchanged`() {
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(uri = "at://did:plc:bob/post/1", authorDid = "did:plc:bob00000000000000000000"),
            )

        val items = response.feed.toFeedItemsUi()

        // No chains; the per-entry pass produces two Singles and the
        // top-level pass leaves them as-is.
        assertEquals(2, items.size)
        val first = assertInstanceOf(FeedItemUi.Single::class.java, items[0])
        val second = assertInstanceOf(FeedItemUi.Single::class.java, items[1])
        assertEquals("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", first.post.id)
        assertEquals("at://did:plc:bob/post/1", second.post.id)
    }

    @Test
    fun `chain followed by Single keeps both as separate items`() {
        val response =
            decodeTimeline(
                feedEntry(uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/1", authorDid = "did:plc:alice000000000000000000"),
                feedEntry(
                    uri = "at://did:plc:alice000000000000000000/app.bsky.feed.post/2",
                    authorDid = "did:plc:alice000000000000000000",
                    replyParent = ReplyParent("at://did:plc:alice000000000000000000/app.bsky.feed.post/1", "did:plc:alice000000000000000000"),
                ),
                feedEntry(uri = "at://did:plc:bob/post/1", authorDid = "did:plc:bob00000000000000000000"),
            )

        val items = response.feed.toFeedItemsUi()

        assertEquals(2, items.size)
        val chain = assertInstanceOf(FeedItemUi.SelfThreadChain::class.java, items[0])
        assertEquals(2, chain.posts.size)
        val single = assertInstanceOf(FeedItemUi.Single::class.java, items[1])
        assertEquals("at://did:plc:bob/post/1", single.post.id)
    }

    // ---------- helpers ----------

    private fun decodeTimeline(vararg entryJson: String): GetTimelineResponse {
        val payload =
            """
            { "feed": [${entryJson.joinToString(",")}] }
            """.trimIndent()
        return json.decodeFromString(GetTimelineResponse.serializer(), payload)
    }

    private data class ReplyParent(
        val uri: String,
        val authorDid: String,
    )

    private fun feedEntry(
        uri: String,
        authorDid: String,
        replyParent: ReplyParent? = null,
        reposterHandle: String? = null,
    ): String {
        val replyBlock =
            if (replyParent == null) {
                ""
            } else {
                """
                "reply": {
                  "root": {
                    "${'$'}type": "app.bsky.feed.defs#postView",
                    "uri": "${replyParent.uri}",
                    "cid": "bafyreifakecid000000000000000000000000000000000",
                    "author": { "did": "${replyParent.authorDid}", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "parent text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    }
                  },
                  "parent": {
                    "${'$'}type": "app.bsky.feed.defs#postView",
                    "uri": "${replyParent.uri}",
                    "cid": "bafyreifakecid000000000000000000000000000000000",
                    "author": { "did": "${replyParent.authorDid}", "handle": "fake.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "parent text",
                      "createdAt": "2026-04-26T12:00:00Z"
                    }
                  }
                },
                """.trimIndent()
            }
        val reasonBlock =
            if (reposterHandle == null) {
                ""
            } else {
                """
                "reason": {
                  "${'$'}type": "app.bsky.feed.defs#reasonRepost",
                  "by": { "did": "$reposterHandle", "handle": "reposter.bsky.social" },
                  "indexedAt": "2026-04-26T12:00:00Z"
                },
                """.trimIndent()
            }
        return """
            {
              "post": {
                "uri": "$uri",
                "cid": "bafyreifakecid000000000000000000000000000000000",
                "author": { "did": "$authorDid", "handle": "fake.bsky.social" },
                "indexedAt": "2026-04-26T12:00:00Z",
                "record": {
                  "${'$'}type": "app.bsky.feed.post",
                  "text": "post text for $uri",
                  "createdAt": "2026-04-26T12:00:00Z"
                }
              },
              $replyBlock
              $reasonBlock
              "indexedAt": "2026-04-26T12:00:00Z"
            }
            """.trimIndent().replace(",\n            }", "\n            }")
    }
}
