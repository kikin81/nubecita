package net.kikin.nubecita.feature.widgets.impl.image

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import net.kikin.nubecita.data.models.ContentWarningCategory
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.MediaContentWarning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Pure per-embed decision for which single thumbnail a widget prefetches (D-C5). */
internal class EmbedThumbnailsTest {
    @Test
    fun `images embed picks the first image thumb`() {
        val embed =
            EmbedUi.Images(
                items =
                    persistentListOf(
                        ImageUi(fullsizeUrl = "full1", thumbUrl = "thumb1", altText = null, aspectRatio = null),
                        ImageUi(fullsizeUrl = "full2", thumbUrl = "thumb2", altText = null, aspectRatio = null),
                    ),
            )

        assertEquals("thumb1", widgetThumbnailUrl(embed))
    }

    @Test
    fun `images embed falls back to fullsize when thumb is absent`() {
        val embed =
            EmbedUi.Images(
                items = persistentListOf(ImageUi(fullsizeUrl = "full1", thumbUrl = null, altText = null, aspectRatio = null)),
            )

        assertEquals("full1", widgetThumbnailUrl(embed))
    }

    @Test
    fun `images embed with a content warning is skipped`() {
        val embed =
            EmbedUi.Images(
                items = persistentListOf(ImageUi(fullsizeUrl = "full1", thumbUrl = "thumb1", altText = null, aspectRatio = null)),
                contentWarning = MediaContentWarning(category = ContentWarningCategory.ADULT_CONTENT, overridable = false),
            )

        assertNull(widgetThumbnailUrl(embed))
    }

    @Test
    fun `video embed picks the poster`() {
        val embed =
            EmbedUi.Video(
                posterUrl = "poster",
                playlistUrl = "https://x/playlist.m3u8",
                aspectRatio = 1.77f,
                durationSeconds = null,
                altText = null,
            )

        assertEquals("poster", widgetThumbnailUrl(embed))
    }

    @Test
    fun `video embed with no poster yields null`() {
        val embed =
            EmbedUi.Video(
                posterUrl = null,
                playlistUrl = "https://x/playlist.m3u8",
                aspectRatio = 1.77f,
                durationSeconds = null,
                altText = null,
            )

        assertNull(widgetThumbnailUrl(embed))
    }

    @Test
    fun `record-with-media recurses into the media half`() {
        val embed =
            EmbedUi.RecordWithMedia(
                record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                media =
                    EmbedUi.Images(
                        items = persistentListOf(ImageUi(fullsizeUrl = "full", thumbUrl = "mediaThumb", altText = null, aspectRatio = null)),
                    ),
            )

        assertEquals("mediaThumb", widgetThumbnailUrl(embed))
    }

    @Test
    fun `text-only embeds yield null`() {
        assertNull(widgetThumbnailUrl(EmbedUi.Empty))
        assertNull(widgetThumbnailUrl(EmbedUi.External(uri = "u", domain = "d", title = "t", description = "x", thumbUrl = "linkThumb")))
        assertNull(widgetThumbnailUrl(EmbedUi.Gif(gifUrl = "g", thumbUrl = "gifThumb", aspectRatio = null, alt = null)))
        assertNull(widgetThumbnailUrl(EmbedUi.Unsupported(typeUri = "app.bsky.embed.future")))
        assertNull(widgetThumbnailUrl(EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.Blocked)))
    }

    @Test
    fun `image count is the gallery size and zero for non-image media`() {
        assertEquals(3, widgetImageCount(images("a", "b", "c")))
        assertEquals(0, widgetImageCount(EmbedUi.Empty))
        assertEquals(
            2,
            widgetImageCount(
                EmbedUi.RecordWithMedia(
                    record = EmbedUi.RecordUnavailable(EmbedUi.RecordUnavailable.Reason.NotFound),
                    media = images("a", "b") as EmbedUi.Images,
                ),
            ),
        )
        assertEquals(0, widgetImageCount(video(poster = "p")))
        // Content-warned media renders no thumbnail → count 0 (no inconsistent "+N").
        assertEquals(
            0,
            widgetImageCount(
                EmbedUi.Images(
                    items = persistentListOf(ImageUi(fullsizeUrl = "f", thumbUrl = "t", altText = null, aspectRatio = null)),
                    contentWarning = MediaContentWarning(category = ContentWarningCategory.ADULT_CONTENT, overridable = false),
                ),
            ),
        )
    }

    @Test
    fun `media description names the medium and total`() {
        assertEquals("Image, 4 total", widgetMediaDescription(images("a", "b", "c", "d")))
        assertEquals("Image", widgetMediaDescription(images("a")))
        assertEquals("Video", widgetMediaDescription(video(poster = "p")))
        assertNull(widgetMediaDescription(video(poster = null)))
        assertNull(widgetMediaDescription(EmbedUi.Empty))
    }

    private companion object {
        fun images(vararg thumbs: String): EmbedUi =
            EmbedUi.Images(
                items = thumbs.map { ImageUi(fullsizeUrl = "full", thumbUrl = it, altText = null, aspectRatio = null) }.toPersistentList(),
            )

        fun video(poster: String?): EmbedUi = EmbedUi.Video(posterUrl = poster, playlistUrl = "https://x/p.m3u8", aspectRatio = 1.77f, durationSeconds = null, altText = null)
    }
}
