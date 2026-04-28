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
    fun `exhaustive when over EmbedUi covers every variant including RecordWithMedia without an else branch`() {
        // Same enforcement pattern as QuotedEmbedUiTest. Adding a future
        // EmbedUi variant without updating this when forces a compile
        // error — the canonical "every dispatch site needs a new arm"
        // surface.
        val variants: List<EmbedUi> =
            listOf(
                EmbedUi.Empty,
                EmbedUi.Images(items = persistentListOf()),
                EmbedUi.Video(
                    posterUrl = null,
                    playlistUrl = "https://example/v.m3u8",
                    aspectRatio = 16f / 9f,
                    durationSeconds = null,
                    altText = null,
                ),
                EmbedUi.External(
                    uri = "https://example.com/article",
                    domain = "example.com",
                    title = "",
                    description = "",
                    thumbUrl = null,
                ),
                EmbedUi.Record(quotedPost = previewQuotedPost()),
                EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                EmbedUi.RecordWithMedia(
                    record = EmbedUi.Record(quotedPost = previewQuotedPost()),
                    media = EmbedUi.Images(items = persistentListOf()),
                ),
                EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew"),
            )
        val labels =
            variants.map { embed ->
                when (embed) {
                    EmbedUi.Empty -> "empty"
                    is EmbedUi.Images -> "images"
                    is EmbedUi.Video -> "video"
                    is EmbedUi.External -> "external"
                    is EmbedUi.Record -> "record"
                    is EmbedUi.RecordUnavailable -> "record-unavailable"
                    is EmbedUi.RecordWithMedia -> "record-with-media"
                    is EmbedUi.Unsupported -> "unsupported"
                }
            }
        assertEquals(
            listOf("empty", "images", "video", "external", "record", "record-unavailable", "record-with-media", "unsupported"),
            labels,
        )
    }

    @Test
    fun `quotedRecord returns the quoted post for top-level Record`() {
        val qp = previewQuotedPost()
        val embed: EmbedUi = EmbedUi.Record(quotedPost = qp)
        assertEquals(qp, embed.quotedRecord)
    }

    @Test
    fun `quotedRecord returns the inner quoted post for RecordWithMedia whose record is Record`() {
        val qp = previewQuotedPost()
        val embed: EmbedUi =
            EmbedUi.RecordWithMedia(
                record = EmbedUi.Record(quotedPost = qp),
                media = EmbedUi.Images(items = persistentListOf()),
            )
        assertEquals(qp, embed.quotedRecord)
    }

    @Test
    fun `quotedRecord returns null for RecordWithMedia whose record is RecordUnavailable`() {
        val embed: EmbedUi =
            EmbedUi.RecordWithMedia(
                record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                media = EmbedUi.Images(items = persistentListOf()),
            )
        assertEquals(null, embed.quotedRecord)
    }

    @Test
    fun `quotedRecord returns null for variants that don't carry a quoted post`() {
        val cases: List<EmbedUi> =
            listOf(
                EmbedUi.Empty,
                EmbedUi.Images(items = persistentListOf()),
                EmbedUi.Video(
                    posterUrl = null,
                    playlistUrl = "https://example/v.m3u8",
                    aspectRatio = 16f / 9f,
                    durationSeconds = null,
                    altText = null,
                ),
                EmbedUi.External(
                    uri = "https://example.com/x",
                    domain = "example.com",
                    title = "",
                    description = "",
                    thumbUrl = null,
                ),
                EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew"),
            )
        cases.forEach { embed ->
            assertEquals(null, embed.quotedRecord, "expected null for $embed")
        }
    }

    private fun previewQuotedPost(): QuotedPostUi =
        QuotedPostUi(
            uri = "at://did:plc:fake/app.bsky.feed.post/q",
            cid = "bafyqcid000000000000000000000000000000000",
            author =
                AuthorUi(
                    did = "did:plc:fake",
                    handle = "fake.bsky.social",
                    displayName = "Fake",
                    avatarUrl = null,
                ),
            createdAt = Instant.fromEpochSeconds(0),
            text = "quoted text",
            facets = persistentListOf(),
            embed = QuotedEmbedUi.Empty,
        )

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
