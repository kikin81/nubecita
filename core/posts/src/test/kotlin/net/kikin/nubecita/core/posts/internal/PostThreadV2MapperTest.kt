package net.kikin.nubecita.core.posts.internal

import io.github.kikin81.atproto.app.bsky.unspecced.GetPostThreadV2Response
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ThreadPositionUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `app.bsky.unspecced.getPostThreadV2` returns a flat, already-ordered list.
 * The mapper keeps that order, turns depth into ancestor / focus / reply, puts
 * the server's thread numbering on the posts and adds a read-more row wherever
 * the response was cut.
 */
internal class PostThreadV2MapperTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    // ---------- the long-thread fixture (shaped like a real 40-post thread) ----------

    @Test
    fun `the order of the response is preserved`() {
        val response = fixture()

        val entries = response.toRawThreadEntries()

        assertEquals(response.thread.map { it.uri.raw }, entries.filter { it.kind != ThreadSnapshotKind.OTHER_REPLIES }.map { it.uri })
    }

    @Test
    fun `the author's thread comes back numbered from the whole thread, not the slice`() {
        val items = fixture().toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        val focus = items.filterIsInstance<ThreadItem.Focus>().single()
        assertEquals(ThreadPositionUi(index = 1, count = 40), focus.post.threadPosition)
        val threadReplies = items.filterIsInstance<ThreadItem.Reply>().filter { it.post.threadPosition != null }
        assertEquals((2..11).map { ThreadPositionUi(index = it, count = 40) }, threadReplies.map { it.post.threadPosition })
        assertEquals((1..10).toList(), threadReplies.map { it.depth })
    }

    @Test
    fun `the last returned post of a longer thread is followed by a read-more row`() {
        val items = fixture().toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        val last = items.indexOfFirst { it is ThreadItem.Reply && it.post.threadPosition?.index == 11 }
        assertEquals(
            ThreadItem.MoreReplies(postUri = uri("op11", OP), count = 9, depth = 10),
            items[last + 1],
        )
    }

    @Test
    fun `a post with more replies whose next item is its own reply gets no row`() {
        val items = fixture().toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        // op4 reports 11 more replies but op5 follows it; r2 reports 1 but r2a follows it.
        val rows = items.filterIsInstance<ThreadItem.MoreReplies>().map { it.postUri }
        assertFalse(uri("op4", OP) in rows)
        assertFalse(rows.any { it.endsWith("/r2") })
        // r3c reports 1 and r3d (its reply) follows; only op11 qualifies in this fixture.
        assertEquals(listOf(uri("op11", OP)), rows)
    }

    @Test
    fun `hasOtherReplies adds one trailing row`() {
        val items = fixture().toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(ThreadItem.OtherReplies, items.last())
        assertEquals(1, items.count { it is ThreadItem.OtherReplies })
    }

    // ---------- hand-built responses ----------

    @Test
    fun `depth below zero is an ancestor, zero the focus, above zero a reply`() {
        val items =
            response(
                post(-2, "a1", index = 1, count = 3),
                post(-1, "a2", index = 2, count = 3),
                post(0, "f", index = 3, count = 3),
                post(1, "r1", did = OTHER),
                post(2, "r1a", did = OTHER),
            ).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(
            listOf("ancestor:a1", "ancestor:a2", "focus:f", "reply:r1@1", "reply:r1a@2"),
            items.map { it.describe() },
        )
        assertEquals(
            listOf(ThreadPositionUi(1, 3), ThreadPositionUi(2, 3), ThreadPositionUi(3, 3), null, null),
            items.map { it.postOrNull()?.threadPosition },
        )
    }

    @Test
    fun `hasOtherReplies false adds no row`() {
        val items = response(post(0, "f")).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("focus:f"), items.map { it.describe() })
    }

    @Test
    fun `a reply chain cut at its end gets a read-more row under its last post`() {
        val items =
            response(
                post(0, "f"),
                post(1, "r1", did = OTHER, moreReplies = 2),
                post(1, "r2", did = OTHER),
            ).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("focus:f", "reply:r1@1", "more:r1x2@1", "reply:r2@1"), items.map { it.describe() })
    }

    @Test
    fun `the focus never gets a read-more row`() {
        val items = response(post(0, "f", moreReplies = 5)).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("focus:f"), items.map { it.describe() })
    }

    @Test
    fun `an ancestor never gets a read-more-replies row`() {
        val items =
            response(post(-1, "a", moreReplies = 4), post(0, "f"))
                .toRawThreadEntries()
                .toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("ancestor:a", "focus:f"), items.map { it.describe() })
    }

    @Test
    fun `the top ancestor with more parents is preceded by a read-earlier row`() {
        val items =
            response(post(-2, "a1", moreParents = true), post(-1, "a2"), post(0, "f"))
                .toRawThreadEntries()
                .toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("parents:a1", "ancestor:a1", "ancestor:a2", "focus:f"), items.map { it.describe() })
    }

    @Test
    fun `blocked, not-found and signed-out-only items become tombstones in place`() {
        val items =
            response(
                other(-1, "gone", "threadItemNotFound"),
                post(0, "f"),
                other(1, "blocked", "threadItemBlocked", """"author": { "did": "$OTHER" }"""),
                other(1, "hidden", "threadItemNoUnauthenticated"),
            ).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("notfound:gone", "focus:f", "blocked:blocked", "notfound:hidden"), items.map { it.describe() })
        assertEquals(OTHER, (items[2] as ThreadItem.Blocked).authorDid)
    }

    @Test
    fun `an item of a kind this app does not know is skipped and the rest still maps`() {
        val items =
            response(
                post(0, "f"),
                other(1, "new", "threadItemFromTheFuture", """"surprise": true"""),
                post(1, "r1", did = OTHER),
            ).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("focus:f", "reply:r1@1"), items.map { it.describe() })
    }

    @Test
    fun `an anchor of a kind this app does not know makes the response unreadable`() {
        // Skipping it would leave a thread with no opened post, which reads
        // as "post not found" when the post is right there.
        val response = response(other(0, "f", "threadItemFromTheFuture"), post(1, "r1", did = OTHER))

        assertThrows(UnreadableOrderedThreadException::class.java) { response.toRawThreadEntries() }
    }

    @Test
    fun `a post item whose shape has drifted is unreadable when it is the anchor`() {
        // The SDK does not fail on a known item it cannot decode: it hands it
        // back as an unknown one. `moreReplies` is required and missing here.
        val drifted = post(0, "f").replace("\"moreReplies\": 0,", "")

        assertThrows(UnreadableOrderedThreadException::class.java) { response(drifted).toRawThreadEntries() }
    }

    @Test
    fun `a response with no anchor at all is unreadable`() {
        assertThrows(UnreadableOrderedThreadException::class.java) { response().toRawThreadEntries() }
        assertThrows(UnreadableOrderedThreadException::class.java) { response(post(1, "r1", did = OTHER)).toRawThreadEntries() }
    }

    @Test
    fun `a post that occurs twice keeps only its first occurrence`() {
        val items =
            response(post(0, "f"), post(1, "r1", did = OTHER), post(1, "r1", did = OTHER), post(1, "r2", did = OTHER))
                .toRawThreadEntries()
                .toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("focus:f", "reply:r1@1", "reply:r2@1"), items.map { it.describe() })
        assertEquals(items.size, items.map { it.key }.toSet().size)
    }

    @Test
    fun `a thread whose anchor is gone has no focus`() {
        val items = response(other(0, "f", "threadItemNotFound")).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("notfound:f"), items.map { it.describe() })
    }

    @Test
    fun `an out-of-range numbering from the server is no position`() {
        val items = response(post(0, "f", index = 9, count = 3)).toRawThreadEntries().toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertNull((items.single() as ThreadItem.Focus).post.threadPosition)
    }

    @Test
    fun `v1-shaped entries still map to a plain thread`() {
        // What a snapshot written before this change holds: no numbering, no cut marks.
        val v2 = response(post(-1, "a"), post(0, "f"), post(1, "r", did = OTHER)).toRawThreadEntries()
        val legacy = v2.map { RawThreadEntry(kind = it.kind, uri = it.uri, depth = it.depth, authorDid = it.authorDid, post = it.post) }

        val items = legacy.toThreadItems(ModerationPrefs.DEFAULT, viewerDid = null)

        assertEquals(listOf("ancestor:a", "focus:f", "reply:r@1"), items.map { it.describe() })
        assertTrue(items.all { it.postOrNull()?.threadPosition == null })
    }

    // ---------- helpers ----------

    private fun ThreadItem.postOrNull() =
        when (this) {
            is ThreadItem.Ancestor -> post
            is ThreadItem.Focus -> post
            is ThreadItem.Reply -> post
            else -> null
        }

    private fun ThreadItem.describe(): String =
        when (this) {
            is ThreadItem.Ancestor -> "ancestor:${post.id.rkey()}"
            is ThreadItem.Focus -> "focus:${post.id.rkey()}"
            is ThreadItem.Reply -> "reply:${post.id.rkey()}@$depth"
            is ThreadItem.MoreParents -> "parents:${postUri.rkey()}"
            is ThreadItem.MoreReplies -> "more:${postUri.rkey()}x$count@$depth"
            ThreadItem.OtherReplies -> "other"
            is ThreadItem.Blocked -> "blocked:${uri.rkey()}"
            is ThreadItem.NotFound -> "notfound:${uri.rkey()}"
        }

    private fun String.rkey() = substringAfterLast('/')

    private fun uri(
        rkey: String,
        did: String,
    ) = "at://$did/app.bsky.feed.post/$rkey"

    private fun fixture(): GetPostThreadV2Response {
        val text =
            requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/thread_v2_long_op_thread.json")) {
                "fixture not found on the test classpath"
            }.bufferedReader().use { it.readText() }
        return json.decodeFromString(GetPostThreadV2Response.serializer(), text)
    }

    private fun response(
        vararg items: String,
        hasOtherReplies: Boolean = false,
    ): GetPostThreadV2Response = json.decodeFromString(GetPostThreadV2Response.serializer(), """{ "thread": [${items.joinToString(",")}], "hasOtherReplies": $hasOtherReplies }""")

    private fun post(
        depth: Int,
        rkey: String,
        did: String = OP,
        index: Int? = null,
        count: Int? = null,
        moreReplies: Int = 0,
        moreParents: Boolean = false,
    ): String =
        """
        {
          "uri": "${uri(rkey, did)}",
          "depth": $depth,
          "value": {
            "${'$'}type": "app.bsky.unspecced.defs#threadItemPost",
            "post": {
              "uri": "${uri(rkey, did)}",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": { "did": "$did", "handle": "fake.test" },
              "record": { "${'$'}type": "app.bsky.feed.post", "text": "post $rkey", "createdAt": "2026-04-26T12:00:00Z" },
              "indexedAt": "2026-04-26T12:00:00Z"
            },
            "moreParents": $moreParents,
            "moreReplies": $moreReplies,
            "opThread": ${index != null},
            ${if (index != null) "\"opThreadPostIndex\": $index," else ""}
            ${if (count != null) "\"opThreadPostCount\": $count," else ""}
            "hiddenByThreadgate": false,
            "mutedByViewer": false
          }
        }
        """.trimIndent()

    private fun other(
        depth: Int,
        rkey: String,
        type: String,
        fields: String = "",
    ): String =
        """
        {
          "uri": "${uri(rkey, OTHER)}",
          "depth": $depth,
          "value": { "${'$'}type": "app.bsky.unspecced.defs#$type"${if (fields.isEmpty()) "" else ", $fields"} }
        }
        """.trimIndent()

    private companion object {
        const val OP = "did:plc:op000000000000000000000"
        const val OTHER = "did:plc:bob00000000000000000000"
    }
}
