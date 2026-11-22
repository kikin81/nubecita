package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.GetTimelineResponse
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.ThreadPositionUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The thread numbering the server puts on a feed entry
 * (`opThreadPostIndex` / `opThreadPostCount`) and on a quoted record reaches
 * the UI models, and nothing else produces a position.
 */
internal class ThreadPositionMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `an entry numbered 3 of 5 sets threadPosition`() {
        val post = decode(entry(rkey = "3", numbering = 3L to 5L)).feed.single().toPostUiOrNull()

        assertEquals(ThreadPositionUi(index = 3, count = 5), post?.threadPosition)
    }

    @Test
    fun `an entry with an out-of-range or partial numbering has no threadPosition`() {
        listOf<Pair<Long?, Long?>>(0L to 5L, 6L to 5L, 3L to null, null to 5L, null to null).forEach { numbering ->
            val post = decode(entry(rkey = "x", numbering = numbering)).feed.single().toPostUiOrNull()

            assertNull(post?.threadPosition, "numbering=$numbering")
        }
    }

    @Test
    fun `a reply cluster with leaf 3 of 5 numbers its root 1 of 5 and its parent 2 of 5`() {
        val item = decode(entry(rkey = "3", numbering = 3L to 5L, replyRoot = "1", replyParent = "2")).feed.toFeedItemsUi().single()

        val cluster = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, item)
        assertEquals(ThreadPositionUi(index = 1, count = 5), cluster.root.threadPosition)
        assertEquals(ThreadPositionUi(index = 2, count = 5), cluster.parent.threadPosition)
        assertEquals(ThreadPositionUi(index = 3, count = 5), cluster.leaf.threadPosition)
    }

    @Test
    fun `a reply cluster with an unnumbered leaf numbers nothing`() {
        val item = decode(entry(rkey = "3", replyRoot = "1", replyParent = "2")).feed.toFeedItemsUi().single()

        val cluster = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, item)
        assertNull(cluster.root.threadPosition)
        assertNull(cluster.parent.threadPosition)
        assertNull(cluster.leaf.threadPosition)
    }

    @Test
    fun `a context post that is also its own numbered entry uses the server number`() {
        // Entry "p" says it is 2 of 9. Entry "leaf" replies to it and says it is
        // 3 of 5: the parent's own number wins over the one derived from the leaf.
        val items =
            decode(
                entry(rkey = "p", numbering = 2L to 9L, authorDid = OTHER),
                entry(rkey = "leaf", numbering = 3L to 5L, replyRoot = "root", replyParent = "p", parentAuthorDid = OTHER),
            ).feed.toFeedItemsUi()

        val cluster = items.filterIsInstance<FeedItemUi.ReplyCluster>().single()
        assertEquals(ThreadPositionUi(index = 2, count = 9), cluster.parent.threadPosition)
        assertEquals(ThreadPositionUi(index = 1, count = 5), cluster.root.threadPosition)
    }

    @Test
    fun `self thread chain members keep their own numbers`() {
        val items =
            decode(
                entry(rkey = "1", numbering = 1L to 3L),
                entry(rkey = "2", numbering = 2L to 3L, replyRoot = "1", replyParent = "1"),
                entry(rkey = "3", numbering = 3L to 3L, replyRoot = "1", replyParent = "2"),
            ).feed.toFeedItemsUi()

        val chain = assertInstanceOf(FeedItemUi.SelfThreadChain::class.java, items.single())
        assertEquals(
            listOf(ThreadPositionUi(1, 3), ThreadPositionUi(2, 3), ThreadPositionUi(3, 3)),
            chain.posts.map { it.threadPosition },
        )
    }

    @Test
    fun `a quoted record numbered 2 of 4 sets threadPosition on the quoted post`() {
        val post = decode(entry(rkey = "q", quotedNumbering = 2L to 4L)).feed.single().toPostUiOrNull()

        val quoted = (post?.embed as EmbedUi.Record).quotedPost
        assertEquals(ThreadPositionUi(index = 2, count = 4), quoted.threadPosition)
    }

    @Test
    fun `a quoted record without numbering has no threadPosition`() {
        val post = decode(entry(rkey = "q", quotedNumbering = null to null)).feed.single().toPostUiOrNull()

        assertNull((post?.embed as EmbedUi.Record).quotedPost.threadPosition)
    }

    // ---------- helpers ----------

    private fun decode(vararg entries: String): GetTimelineResponse = json.decodeFromString(GetTimelineResponse.serializer(), """{ "feed": [${entries.joinToString(",")}] }""")

    private fun uri(
        rkey: String,
        did: String = AUTHOR,
    ) = "at://$did/app.bsky.feed.post/$rkey"

    private fun postView(
        rkey: String,
        did: String,
        embed: String = "",
        typed: Boolean = false,
    ): String =
        """
        {
          ${if (typed) "\"${'$'}type\": \"app.bsky.feed.defs#postView\"," else ""}
          "uri": "${uri(rkey, did)}",
          "cid": "bafyreifakecid000000000000000000000000000000000",
          "author": { "did": "$did", "handle": "fake.bsky.social" },
          "indexedAt": "2026-04-26T12:00:00Z",
          $embed
          "record": { "${'$'}type": "app.bsky.feed.post", "text": "post $rkey", "createdAt": "2026-04-26T12:00:00Z" }
        }
        """.trimIndent()

    private fun numberingFields(
        numbering: Pair<Long?, Long?>,
        trailingComma: Boolean = true,
    ): String =
        listOfNotNull(
            numbering.first?.let { "\"opThreadPostIndex\": $it" },
            numbering.second?.let { "\"opThreadPostCount\": $it" },
        ).joinToString(",").let { if (it.isNotEmpty() && trailingComma) "$it," else it }

    private fun entry(
        rkey: String,
        numbering: Pair<Long?, Long?> = null to null,
        authorDid: String = AUTHOR,
        replyRoot: String? = null,
        replyParent: String? = null,
        parentAuthorDid: String = AUTHOR,
        quotedNumbering: Pair<Long?, Long?>? = null,
    ): String {
        val reply =
            if (replyRoot == null || replyParent == null) {
                ""
            } else {
                """
                "reply": {
                  "root": ${postView(replyRoot, AUTHOR, typed = true)},
                  "parent": ${postView(replyParent, parentAuthorDid, typed = true)}
                },
                """.trimIndent()
            }
        val embed =
            if (quotedNumbering == null) {
                ""
            } else {
                """
                "embed": {
                  "${'$'}type": "app.bsky.embed.record#view",
                  "record": {
                    "${'$'}type": "app.bsky.embed.record#viewRecord",
                    "uri": "${uri("quoted", OTHER)}",
                    "cid": "bafyreifakecid000000000000000000000000000000000",
                    "author": { "did": "$OTHER", "handle": "other.bsky.social" },
                    "indexedAt": "2026-04-26T12:00:00Z",
                    ${numberingFields(quotedNumbering)}
                    "value": { "${'$'}type": "app.bsky.feed.post", "text": "quoted", "createdAt": "2026-04-26T12:00:00Z" }
                  }
                },
                """.trimIndent()
            }
        return """
            {
              $reply
              ${numberingFields(numbering)}
              "post": ${postView(rkey, authorDid, embed)}
            }
            """.trimIndent()
    }

    private companion object {
        const val AUTHOR = "did:plc:alice000000000000000000"
        const val OTHER = "did:plc:bob00000000000000000000"
    }
}
