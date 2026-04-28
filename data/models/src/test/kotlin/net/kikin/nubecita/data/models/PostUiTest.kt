package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class PostUiTest {
    @Test
    fun `two PostUi with identical content are structurally equal`() {
        val a = PostUiFixtures.fakePost(id = "p1", text = "hello")
        val b = PostUiFixtures.fakePost(id = "p1", text = "hello")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `differing id makes posts unequal`() {
        val a = PostUiFixtures.fakePost(id = "p1")
        val b = PostUiFixtures.fakePost(id = "p2")
        assertNotEquals(a, b)
    }

    @Test
    fun `differing viewer state makes posts unequal`() {
        val a = PostUiFixtures.fakePost(viewer = ViewerStateUi(isLikedByViewer = false))
        val b = PostUiFixtures.fakePost(viewer = ViewerStateUi(isLikedByViewer = true))
        assertNotEquals(a, b)
    }

    @Test
    fun `Empty embed is the canonical no-embed value`() {
        val post = PostUiFixtures.fakePost(embed = EmbedUi.Empty)
        assertEquals(EmbedUi.Empty, post.embed)
    }

    @Test
    fun `Unsupported embed carries the type URI for debug labeling`() {
        val embed = EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")
        assertEquals("app.bsky.embed.video", embed.typeUri)
    }

    @Test
    fun `RecordUnavailable Reason enumerates exactly the four expected variants in stable order`() {
        // The render layer collapses all four to a single chip ("Quoted
        // post unavailable") per the YAGNI design — but the wire-side
        // distinction is preserved for telemetry / future per-variant
        // copy upgrades. Adding a 5th Reason should break this test and
        // force a deliberate decision about copy + UI implications.
        assertEquals(
            listOf(
                EmbedUi.RecordUnavailable.Reason.NotFound,
                EmbedUi.RecordUnavailable.Reason.Blocked,
                EmbedUi.RecordUnavailable.Reason.Detached,
                EmbedUi.RecordUnavailable.Reason.Unknown,
            ),
            EmbedUi.RecordUnavailable.Reason
                .values()
                .toList(),
        )
    }

    @Test
    fun `RecordUnavailable variants with the same Reason are structurally equal`() {
        val a = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound)
        val b = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `PostStatsUi defaults are all zero`() {
        val stats = PostStatsUi()
        assertEquals(0, stats.replyCount)
        assertEquals(0, stats.repostCount)
        assertEquals(0, stats.likeCount)
        assertEquals(0, stats.quoteCount)
    }

    @Test
    fun `ViewerStateUi defaults are all false`() {
        val viewer = ViewerStateUi()
        assertEquals(false, viewer.isLikedByViewer)
        assertEquals(false, viewer.isRepostedByViewer)
        assertEquals(false, viewer.isFollowingAuthor)
    }

    @Test
    fun `createdAt round-trips through Instant_parse`() {
        val raw = "2026-04-25T12:00:00Z"
        val post = PostUiFixtures.fakePost(createdAt = Instant.parse(raw))
        assertEquals(raw, post.createdAt.toString())
    }

    @Test
    fun `facets list is immutable and structurally compared`() {
        val a = PostUiFixtures.fakePost(facets = persistentListOf())
        val b = PostUiFixtures.fakePost(facets = persistentListOf())
        assertEquals(a.facets, b.facets)
    }
}
