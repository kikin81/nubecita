package net.kikin.nubecita.feature.postdetail.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.app.bsky.feed.BlockedAuthor
import io.github.kikin81.atproto.app.bsky.feed.BlockedPost
import io.github.kikin81.atproto.app.bsky.feed.NotFoundPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPost
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostParentUnion
import io.github.kikin81.atproto.app.bsky.feed.ThreadViewPostRepliesUnion
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Cid
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class PostThreadMapperTest {
    @Test
    fun `top-level threadViewPost with no parent and no replies yields single Focus`() {
        val thread = threadViewPost(uri = "at://focus", text = "focus body")

        val items = thread.toThreadItems()

        assertEquals(1, items.size)
        val focus = items.single()
        assertTrue(focus is ThreadItem.Focus)
        assertEquals("at://focus", (focus as ThreadItem.Focus).post.id)
    }

    @Test
    fun `single-level parent renders as Ancestor before Focus`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                parent = threadViewPost(uri = "at://parent", text = "parent"),
            )

        val items = thread.toThreadItems()

        assertEquals(2, items.size)
        val ancestor = items[0] as ThreadItem.Ancestor
        val focus = items[1] as ThreadItem.Focus
        assertEquals("at://parent", ancestor.post.id)
        assertEquals("at://focus", focus.post.id)
    }

    @Test
    fun `multi-level parent chain emits Ancestors root-most first`() {
        val grandparent = threadViewPost(uri = "at://gp", text = "grandparent")
        val parent =
            threadViewPost(
                uri = "at://p",
                text = "parent",
                parent = grandparent,
            )
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                parent = parent,
            )

        val items = thread.toThreadItems()

        assertEquals(3, items.size)
        assertEquals("at://gp", (items[0] as ThreadItem.Ancestor).post.id)
        assertEquals("at://p", (items[1] as ThreadItem.Ancestor).post.id)
        assertEquals("at://focus", (items[2] as ThreadItem.Focus).post.id)
    }

    @Test
    fun `top-level replies emit Reply with depth=1`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                replies =
                    listOf(
                        threadViewPost(uri = "at://r1", text = "r1"),
                        threadViewPost(uri = "at://r2", text = "r2"),
                    ),
            )

        val items = thread.toThreadItems()

        assertEquals(3, items.size)
        assertTrue(items[0] is ThreadItem.Focus)
        val r1 = items[1] as ThreadItem.Reply
        val r2 = items[2] as ThreadItem.Reply
        assertEquals("at://r1", r1.post.id)
        assertEquals(1, r1.depth)
        assertEquals("at://r2", r2.post.id)
        assertEquals(1, r2.depth)
    }

    @Test
    fun `nested replies emit depth-first traversal with incrementing depth`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                replies =
                    listOf(
                        threadViewPost(
                            uri = "at://r1",
                            text = "r1",
                            replies =
                                listOf(
                                    threadViewPost(uri = "at://r1c1", text = "r1c1"),
                                    threadViewPost(uri = "at://r1c2", text = "r1c2"),
                                ),
                        ),
                        threadViewPost(uri = "at://r2", text = "r2"),
                    ),
            )

        val items = thread.toThreadItems()

        // focus, r1 (d=1), r1c1 (d=2), r1c2 (d=2), r2 (d=1)
        assertEquals(5, items.size)
        assertEquals("at://focus", (items[0] as ThreadItem.Focus).post.id)
        (items[1] as ThreadItem.Reply).run {
            assertEquals("at://r1", post.id)
            assertEquals(1, depth)
        }
        (items[2] as ThreadItem.Reply).run {
            assertEquals("at://r1c1", post.id)
            assertEquals(2, depth)
        }
        (items[3] as ThreadItem.Reply).run {
            assertEquals("at://r1c2", post.id)
            assertEquals(2, depth)
        }
        (items[4] as ThreadItem.Reply).run {
            assertEquals("at://r2", post.id)
            assertEquals(1, depth)
        }
    }

    @Test
    fun `blockedPost in parent slot emits Blocked then Focus`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                parent = blockedPost(uri = "at://blocked-parent"),
            )

        val items = thread.toThreadItems()

        assertEquals(2, items.size)
        val blocked = items[0] as ThreadItem.Blocked
        assertEquals("at://blocked-parent", blocked.uri)
        assertEquals("at://focus", (items[1] as ThreadItem.Focus).post.id)
    }

    @Test
    fun `notFoundPost in parent slot emits NotFound then Focus`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                parent = notFoundPost(uri = "at://nf-parent"),
            )

        val items = thread.toThreadItems()

        assertEquals(2, items.size)
        val notFound = items[0] as ThreadItem.NotFound
        assertEquals("at://nf-parent", notFound.uri)
        assertEquals("at://focus", (items[1] as ThreadItem.Focus).post.id)
    }

    @Test
    fun `blockedPost in replies slot emits Blocked after Focus`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                replies = listOf(blockedPost(uri = "at://blocked-reply")),
            )

        val items = thread.toThreadItems()

        assertEquals(2, items.size)
        assertTrue(items[0] is ThreadItem.Focus)
        val blocked = items[1] as ThreadItem.Blocked
        assertEquals("at://blocked-reply", blocked.uri)
    }

    @Test
    fun `notFoundPost in replies slot emits NotFound after Focus`() {
        val thread =
            threadViewPost(
                uri = "at://focus",
                text = "focus",
                replies = listOf(notFoundPost(uri = "at://nf-reply")),
            )

        val items = thread.toThreadItems()

        assertEquals(2, items.size)
        assertTrue(items[0] is ThreadItem.Focus)
        val notFound = items[1] as ThreadItem.NotFound
        assertEquals("at://nf-reply", notFound.uri)
    }

    @Test
    fun `top-level blockedPost yields single Blocked item`() {
        val thread = blockedPost(uri = "at://blocked-focus")

        val items = thread.toThreadItems()

        assertEquals(1, items.size)
        val blocked = items.single() as ThreadItem.Blocked
        assertEquals("at://blocked-focus", blocked.uri)
    }

    @Test
    fun `top-level notFoundPost yields single NotFound item`() {
        val thread = notFoundPost(uri = "at://nf-focus")

        val items = thread.toThreadItems()

        assertEquals(1, items.size)
        val notFound = items.single() as ThreadItem.NotFound
        assertEquals("at://nf-focus", notFound.uri)
    }

    @Test
    fun `focus with malformed record JSON yields empty list`() {
        // PostView record without the required `text` / `createdAt` fields.
        // toPostUiCore returns null; the mapper's ThreadViewPost branch
        // bails out with persistentListOf() — same surface the VM treats
        // as a NotFound thread.
        val thread =
            ThreadViewPost(
                post =
                    postView(
                        uri = "at://focus",
                        record = buildJsonObject { put("\$type", "app.bsky.feed.post") },
                    ),
            )

        val items = thread.toThreadItems()

        assertTrue(items.isEmpty())
    }

    // ---------- fixture helpers ----------

    private fun threadViewPost(
        uri: String,
        text: String,
        parent: ThreadViewPostParentUnion? = null,
        replies: List<ThreadViewPostRepliesUnion>? = null,
    ): ThreadViewPost =
        ThreadViewPost(
            parent = parent,
            post = postView(uri = uri, record = postRecord(text = text)),
            replies = replies,
        )

    private fun postView(
        uri: String,
        record: JsonObject,
    ): PostView =
        PostView(
            author =
                ProfileViewBasic(
                    did = Did("did:plc:test"),
                    handle = Handle("test.bsky.social"),
                    displayName = "Test User",
                ),
            cid = Cid("bafyreitestcidvalue"),
            indexedAt = Datetime("2026-04-25T12:00:00Z"),
            record = record,
            uri = AtUri(uri),
        )

    private fun postRecord(
        text: String,
        createdAt: String = "2026-04-25T12:00:00Z",
    ): JsonObject =
        buildJsonObject {
            put("\$type", "app.bsky.feed.post")
            put("text", text)
            put("createdAt", createdAt)
        }

    private fun blockedPost(uri: String): BlockedPost =
        BlockedPost(
            author = BlockedAuthor(did = Did("did:plc:blocked")),
            blocked = true,
            uri = AtUri(uri),
        )

    private fun notFoundPost(uri: String): NotFoundPost =
        NotFoundPost(
            notFound = true,
            uri = AtUri(uri),
        )
}
