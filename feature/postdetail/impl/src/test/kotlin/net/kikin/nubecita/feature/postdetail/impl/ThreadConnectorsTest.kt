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
 * Where post detail draws thread lines: earlier posts are joined down into the
 * opened post, nothing leaves the opened post downward, and below it a reply is
 * joined to the row under it only when that row is its own reply.
 */
internal class ThreadConnectorsTest {
    private fun flags(vararg items: ThreadItem) = items.toList().connectorFlags()

    private fun line(
        above: Boolean,
        below: Boolean,
    ) = ConnectorFlags(above = above, below = below)

    @Test
    fun `opened at post 3 of 5, the earlier posts join into it and the later ones join each other`() {
        val result = flags(ancestor("1"), ancestor("2"), focus("3"), reply("4", depth = 1), reply("5", depth = 2), reply("x", depth = 1))

        assertEquals(
            listOf(
                line(above = false, below = true),
                line(above = true, below = true),
                // Into the opened post from above, and nothing out of it.
                line(above = true, below = false),
                line(above = false, below = true),
                line(above = true, below = false),
                line(above = false, below = false),
            ),
            result,
        )
    }

    @Test
    fun `an opened post with nothing above it has no line`() {
        assertEquals(listOf(line(above = false, below = false), line(above = false, below = false)), flags(focus("1"), reply("r", depth = 1)))
    }

    @Test
    fun `a read-earlier row joins into the first ancestor`() {
        val result = flags(ThreadItem.MoreParents(postUri = "at://1"), ancestor("1"), focus("2"))

        assertEquals(line(above = false, below = true), result[0])
        assertEquals(line(above = true, below = true), result[1])
    }

    @Test
    fun `a reply joins into its own read-more row`() {
        val result = flags(focus("f"), reply("r", depth = 1), ThreadItem.MoreReplies(postUri = "at://r", count = 3, depth = 1))

        assertEquals(line(above = false, below = true), result[1])
        assertEquals(line(above = true, below = false), result[2])
    }

    @Test
    fun `a reply does not join a sibling or a shallower row`() {
        val result = flags(focus("f"), reply("a", depth = 1), reply("a1", depth = 2), reply("b", depth = 1), reply("c", depth = 1))

        assertEquals(
            listOf(false to true, true to false, false to false, false to false),
            result.drop(1).map { it.above to it.below },
        )
    }

    @Test
    fun `no line runs into or out of a tombstone in the ancestor chain`() {
        val result =
            flags(
                ancestor("1"),
                ThreadItem.Blocked(uri = "at://blocked", authorDid = "did:plc:b"),
                ancestor("2"),
                ThreadItem.NotFound(uri = "at://gone"),
                focus("3"),
            )

        assertEquals(
            listOf(
                line(above = false, below = false),
                line(above = false, below = false),
                line(above = false, below = false),
                line(above = false, below = false),
                line(above = false, below = false),
            ),
            result,
        )
    }

    @Test
    fun `the show-more-replies row is never joined`() {
        val result = flags(focus("f"), reply("r", depth = 1), ThreadItem.OtherReplies)

        assertEquals(line(above = false, below = false), result[1])
        assertEquals(line(above = false, below = false), result[2])
    }

    @Test
    fun `there is one entry per row, in order`() {
        assertEquals(emptyList<ConnectorFlags>(), emptyList<ThreadItem>().connectorFlags())
        assertEquals(3, flags(ancestor("1"), focus("2"), reply("3", depth = 1)).size)
    }

    private fun ancestor(id: String) = ThreadItem.Ancestor(post("at://$id"))

    private fun focus(id: String) = ThreadItem.Focus(post("at://$id"))

    private fun reply(
        id: String,
        depth: Int,
    ) = ThreadItem.Reply(post = post("at://$id"), depth = depth)

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
