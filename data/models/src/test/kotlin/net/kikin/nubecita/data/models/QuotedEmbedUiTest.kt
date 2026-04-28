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
                QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia"),
            )
        val labels =
            variants.map { embed ->
                when (embed) {
                    QuotedEmbedUi.Empty -> "empty"
                    is QuotedEmbedUi.Images -> "images"
                    is QuotedEmbedUi.Video -> "video"
                    is QuotedEmbedUi.External -> "external"
                    QuotedEmbedUi.QuotedThreadChip -> "thread-chip"
                    is QuotedEmbedUi.Unsupported -> "unsupported"
                }
            }
        assertEquals(
            listOf("empty", "images", "video", "external", "thread-chip", "unsupported"),
            labels,
        )
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
}
