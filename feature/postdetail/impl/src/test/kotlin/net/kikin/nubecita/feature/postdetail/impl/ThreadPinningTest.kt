package net.kikin.nubecita.feature.postdetail.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * The two decisions behind "the opened post is at the top, earlier posts are
 * above it": which rows the list shows before the opened post has been laid
 * out, and how much room the list needs under a short thread for the opened
 * post to be able to sit at the top at all.
 */
internal class ThreadPinningTest {
    private val a1 = ThreadItem.Ancestor(post("at://a1"))
    private val a2 = ThreadItem.Ancestor(post("at://a2"))
    private val focus = ThreadItem.Focus(post("at://f"))
    private val reply = ThreadItem.Reply(post = post("at://r"), depth = 1)

    // ---------- which rows are shown ----------

    @Test
    fun `before the opened post is placed, the rows above it are held back`() {
        val rows = listOf(ThreadItem.MoreParents("at://a1"), a1, a2, focus, reply)

        assertEquals(listOf(focus, reply), rows.rowsToDisplay(leadingRowsShown = false))
    }

    @Test
    fun `once it is placed, every row is shown in order`() {
        val rows = listOf(a1, a2, focus, reply)

        assertEquals(rows, rows.rowsToDisplay(leadingRowsShown = true))
    }

    @Test
    fun `a thread that starts at the opened post is shown whole either way`() {
        val rows = listOf(focus, reply)

        assertEquals(rows, rows.rowsToDisplay(leadingRowsShown = false))
        assertEquals(0, rows.leadingRowCount())
    }

    @Test
    fun `a thread with no opened post is shown as it is`() {
        val rows = listOf<ThreadItem>(ThreadItem.NotFound("at://gone"))

        assertEquals(rows, rows.rowsToDisplay(leadingRowsShown = false))
        assertEquals(0, rows.leadingRowCount())
    }

    @Test
    fun `leading rows are counted up to the opened post`() {
        assertEquals(2, listOf(a1, a2, focus, reply).leadingRowCount())
    }

    // ---------- room under a short thread ----------

    private fun row(
        index: Int,
        key: String,
        size: Int,
    ) = ThreadRowInfo(index = index, key = key, size = size)

    @Test
    fun `nothing is known until the list has laid out`() {
        assertNull(trailingSpaceToPinFocus(visible = emptyList(), focusKey = focus.key, lastContentIndex = 1, viewportContentHeight = 1000))
    }

    @Test
    fun `a short tail gets the room it lacks`() {
        // The opened post (300) and one reply (200) under a 1000px viewport.
        val visible = listOf(row(0, focus.key, 300), row(1, reply.key, 200), row(2, "spacer", 0))

        assertEquals(500, trailingSpaceToPinFocus(visible, focusKey = focus.key, lastContentIndex = 1, viewportContentHeight = 1000))
    }

    @Test
    fun `the room does not count itself`() {
        // Same thread a frame later, the spacer now 500 tall and on screen.
        val visible = listOf(row(0, focus.key, 300), row(1, reply.key, 200), row(2, "spacer", 500))

        assertEquals(500, trailingSpaceToPinFocus(visible, focusKey = focus.key, lastContentIndex = 1, viewportContentHeight = 1000))
    }

    @Test
    fun `a tail that already fills the viewport needs none`() {
        val visible = listOf(row(0, focus.key, 700), row(1, reply.key, 600))

        assertEquals(0, trailingSpaceToPinFocus(visible, focusKey = focus.key, lastContentIndex = 1, viewportContentHeight = 1000))
    }

    @Test
    fun `a tail that runs past the viewport needs none`() {
        // Rows 2..9 exist but are not on screen yet.
        val visible = listOf(row(0, focus.key, 300), row(1, reply.key, 200))

        assertEquals(0, trailingSpaceToPinFocus(visible, focusKey = focus.key, lastContentIndex = 9, viewportContentHeight = 1000))
    }

    @Test
    fun `rows above the opened post are not part of the tail`() {
        val visible = listOf(row(0, a1.key, 400), row(1, focus.key, 300), row(2, reply.key, 200), row(3, "spacer", 0))

        assertEquals(500, trailingSpaceToPinFocus(visible, focusKey = focus.key, lastContentIndex = 2, viewportContentHeight = 1000))
    }

    @Test
    fun `with the opened post off screen there is nothing to pin`() {
        val visible = listOf(row(4, reply.key, 200))

        assertEquals(0, trailingSpaceToPinFocus(visible, focusKey = focus.key, lastContentIndex = 9, viewportContentHeight = 1000))
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
