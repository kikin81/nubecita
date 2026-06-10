package net.kikin.nubecita.feature.widgets.impl.image

import kotlinx.collections.immutable.persistentListOf
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
}
