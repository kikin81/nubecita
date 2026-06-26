package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class QuotedEmbedUiTest {
    /**
     * Compile-time exhaustiveness check: this `when` over
     * [QuotedEmbedUi] has NO `else` arm. If a variant is added to
     * the sealed interface without an arm here, the build fails.
     *
     * Critically — there MUST NOT be a `Record` arm. The deliberate
     * absence of a Record variant is what enforces the one-level
     * recursion bound at the type system; if a future change adds
     * `Record` to QuotedEmbedUi, the recursion bound is broken and
     * this test should be re-examined (along with every dispatch
     * site).
     */
    @Test
    fun `exhaustive when over QuotedEmbedUi compiles without an else branch`() {
        val variants: List<QuotedEmbedUi> =
            listOf(
                QuotedEmbedUi.Empty,
                QuotedEmbedUi.Images(items = persistentListOf()),
                QuotedEmbedUi.Gallery(items = persistentListOf()),
                QuotedEmbedUi.Video(
                    posterUrl = null,
                    playlistUrl = "https://example/v.m3u8",
                    aspectRatio = 16f / 9f,
                    durationSeconds = null,
                    altText = null,
                ),
                QuotedEmbedUi.External(
                    uri = "https://example.com/article",
                    domain = "example.com",
                    title = "Article",
                    description = "",
                    thumbUrl = null,
                ),
                QuotedEmbedUi.QuotedThreadChip,
                QuotedEmbedUi.RecordWithMedia(media = QuotedEmbedUi.Images(items = persistentListOf())),
                QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew"),
                QuotedEmbedUi.Gif(
                    gifUrl = "https://static.klipy.com/example.gif",
                    thumbUrl = null,
                    aspectRatio = 1f,
                    alt = null,
                ),
            )
        val labels =
            variants.map { embed ->
                when (embed) {
                    QuotedEmbedUi.Empty -> "empty"
                    is QuotedEmbedUi.Images -> "images"
                    is QuotedEmbedUi.Gallery -> "gallery"
                    is QuotedEmbedUi.Video -> "video"
                    is QuotedEmbedUi.External -> "external"
                    QuotedEmbedUi.QuotedThreadChip -> "thread-chip"
                    is QuotedEmbedUi.RecordWithMedia -> "record-with-media"
                    is QuotedEmbedUi.Unsupported -> "unsupported"
                    is QuotedEmbedUi.Gif -> "gif"
                }
            }
        assertEquals(
            listOf("empty", "images", "gallery", "video", "external", "thread-chip", "record-with-media", "unsupported", "gif"),
            labels,
        )
    }

    /**
     * The [QuotedEmbedUi.MediaEmbed] marker constrains the
     * [QuotedEmbedUi.RecordWithMedia.media] slot to exactly five
     * variants. The exhaustive `when` below — with no `else` arm —
     * fails to compile if a future variant joins the marker without
     * an arm here, or if a variant is removed from the marker.
     */
    @Test
    fun `MediaEmbed marker has exactly five implementers`() {
        val variants: List<QuotedEmbedUi.MediaEmbed> =
            listOf(
                QuotedEmbedUi.Images(items = persistentListOf()),
                QuotedEmbedUi.Gallery(items = persistentListOf()),
                QuotedEmbedUi.Video(
                    posterUrl = null,
                    playlistUrl = "https://example/v.m3u8",
                    aspectRatio = 16f / 9f,
                    durationSeconds = null,
                    altText = null,
                ),
                QuotedEmbedUi.External(
                    uri = "https://example.com/article",
                    domain = "example.com",
                    title = "Article",
                    description = "",
                    thumbUrl = null,
                ),
                QuotedEmbedUi.Gif(
                    gifUrl = "https://static.klipy.com/example.gif",
                    thumbUrl = null,
                    aspectRatio = 1f,
                    alt = null,
                ),
            )
        val labels =
            variants.map { media ->
                when (media) {
                    is QuotedEmbedUi.Images -> "images"
                    is QuotedEmbedUi.Gallery -> "gallery"
                    is QuotedEmbedUi.Video -> "video"
                    is QuotedEmbedUi.External -> "external"
                    is QuotedEmbedUi.Gif -> "gif"
                }
            }
        assertEquals(listOf("images", "gallery", "video", "external", "gif"), labels)
    }

    @Test
    fun `Images and Gallery share the ImageContainerEmbed dispatch arm`() {
        val items = persistentListOf(ImageUi(fullsizeUrl = "f", thumbUrl = "t", altText = "a", aspectRatio = 1.5f))
        val embeds: List<QuotedEmbedUi> =
            listOf(QuotedEmbedUi.Images(items = items), QuotedEmbedUi.Gallery(items = items))
        embeds.forEach { embed ->
            val extracted =
                when (embed) {
                    is QuotedEmbedUi.ImageContainerEmbed -> embed.items
                    else -> error("expected ImageContainerEmbed, got $embed")
                }
            assertEquals(items, extracted)
        }
    }

    @Test
    fun `Empty and QuotedThreadChip are singletons`() {
        assertEquals(QuotedEmbedUi.Empty, QuotedEmbedUi.Empty)
        assertEquals(QuotedEmbedUi.QuotedThreadChip, QuotedEmbedUi.QuotedThreadChip)
    }

    @Test
    fun `Images payload is a value-equal data class`() {
        val a = QuotedEmbedUi.Images(items = persistentListOf())
        val b = QuotedEmbedUi.Images(items = persistentListOf())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `RecordWithMedia is a value-equal data class`() {
        val a = QuotedEmbedUi.RecordWithMedia(media = QuotedEmbedUi.Images(items = persistentListOf()))
        val b = QuotedEmbedUi.RecordWithMedia(media = QuotedEmbedUi.Images(items = persistentListOf()))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
