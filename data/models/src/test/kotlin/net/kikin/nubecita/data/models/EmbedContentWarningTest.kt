package net.kikin.nubecita.data.models

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Contract for the pure [withMediaContentWarning] / [withContentWarning]
 * helpers — the single seam the moderation layer uses to stamp a precomputed
 * cover onto a post's media off the render path.
 */
class EmbedContentWarningTest {
    private val warn = MediaContentWarning(ContentWarningCategory.ADULT_CONTENT, overridable = true)

    private fun image() = ImageUi(fullsizeUrl = "https://cdn/full.jpg", thumbUrl = null, altText = null, aspectRatio = null)

    private fun video() = EmbedUi.Video(posterUrl = null, playlistUrl = "https://cdn/p.m3u8", aspectRatio = 1.77f, durationSeconds = null, altText = null)

    private fun external() = EmbedUi.External(uri = "https://x.test", domain = "x.test", title = "t", description = "d", thumbUrl = null)

    private fun gif() = EmbedUi.Gif(gifUrl = "https://x.test/a.gif", thumbUrl = null, aspectRatio = null, alt = null)

    @Test
    fun `stamps each media embed variant`() {
        assertEquals(warn, (EmbedUi.Images(persistentListOf(image())).withMediaContentWarning(warn) as EmbedUi.Images).contentWarning)
        assertEquals(warn, (video().withMediaContentWarning(warn) as EmbedUi.Video).contentWarning)
        assertEquals(warn, (external().withMediaContentWarning(warn) as EmbedUi.External).contentWarning)
        assertEquals(warn, (gif().withMediaContentWarning(warn) as EmbedUi.Gif).contentWarning)
    }

    @Test
    fun `null clears an existing warning`() {
        val covered = gif().copy(contentWarning = warn)
        assertNull((covered.withMediaContentWarning(null) as EmbedUi.Gif).contentWarning)
    }

    @Test
    fun `record-with-media covers only the media half`() {
        val rwm =
            EmbedUi.RecordWithMedia(
                record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked),
                media = video(),
            )
        val result = rwm.withMediaContentWarning(warn) as EmbedUi.RecordWithMedia
        assertEquals(warn, result.media.contentWarning)
        // The quoted record slot is untouched.
        assertEquals(EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked), result.record)
    }

    @Test
    fun `non-media embeds are returned unchanged`() {
        val unsupported = EmbedUi.Unsupported(typeUri = "app.bsky.embed.future")
        assertSame(EmbedUi.Empty, EmbedUi.Empty.withMediaContentWarning(warn))
        assertSame(unsupported, unsupported.withMediaContentWarning(warn))
    }
}
