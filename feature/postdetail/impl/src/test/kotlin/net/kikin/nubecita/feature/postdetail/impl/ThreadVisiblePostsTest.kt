package net.kikin.nubecita.feature.postdetail.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Which thread posts are on screen, for the translation settle.
 *
 * Indices would be wrong here: the list interleaves outgoing replies and
 * tombstones between thread posts, and every such row above a post would
 * shift the translation onto its neighbour.
 */
internal class ThreadVisiblePostsTest {
    private val ancestor = ThreadItem.Ancestor(post("at://a"))
    private val focus = ThreadItem.Focus(post("at://f"))
    private val reply = ThreadItem.Reply(post = post("at://r"), depth = 1)
    private val items = listOf(ancestor, focus, ThreadItem.Blocked(uri = "at://b", authorDid = "did:plc:b"), reply)

    @Test
    fun `visible thread posts resolve by key, in screen order`() {
        val visible = listOf(ancestor.key, focus.key, reply.key)

        assertEquals(listOf("at://a", "at://f", "at://r"), visibleThreadPosts(visible, items).map { it.id })
    }

    @Test
    fun `rows that aren't thread posts contribute nothing and shift nothing`() {
        // An outgoing reply and a blocked tombstone sit between the focus and
        // the reply. By index, the reply would have been read as the row
        // above it.
        val visible = listOf(focus.key, "outgoing:local-1", ThreadItem.Blocked(uri = "at://b", authorDid = "did:plc:b").key, reply.key)

        assertEquals(listOf("at://f", "at://r"), visibleThreadPosts(visible, items).map { it.id })
    }

    @Test
    fun `nothing laid out is nothing to translate`() {
        assertEquals(emptyList<PostUi>(), visibleThreadPosts(emptyList(), items))
    }

    private fun post(id: String) =
        PostUi(
            id = id,
            cid = "bafy",
            author = AuthorUi("did:plc:a", "a.bsky.social", "A", null),
            createdAt = Instant.fromEpochSeconds(0),
            text = "text",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
