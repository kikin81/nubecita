package net.kikin.nubecita.core.posttranslation.ui

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/** Visible posts for a list keyed by post uri, the shape most post lists have. */
internal class VisiblePostsTest {
    private val posts = listOf(post("at://a"), post("at://b"), post("at://c"))

    @Test
    fun `visible posts resolve by key, in screen order`() {
        assertEquals(listOf("at://b", "at://c"), visiblePostsById(listOf("at://b", "at://c"), posts).map { it.id })
    }

    @Test
    fun `rows that aren't posts are skipped, not shifted onto a neighbour`() {
        // A sort row above and a loading row below, as in search results.
        val visible = listOf("sort-row", "at://a", "at://b", "appending-indicator")

        assertEquals(listOf("at://a", "at://b"), visiblePostsById(visible, posts).map { it.id })
    }

    @Test
    fun `nothing laid out is nothing to translate`() {
        assertEquals(emptyList<PostUi>(), visiblePostsById(emptyList(), posts))
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
