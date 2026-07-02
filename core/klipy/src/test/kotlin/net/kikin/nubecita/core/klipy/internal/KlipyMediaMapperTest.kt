package net.kikin.nubecita.core.klipy.internal

import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.KlipyMediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KlipyMediaMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `polymorphic deserializer selects concrete DTO by type`() {
        val data = decode(MIXED_PAGE).data?.data.orEmpty()

        assertTrue(data[0] is KlipyMediaItemDto.General)
        assertTrue(data[1] is KlipyMediaItemDto.General)
        assertTrue(data[2] is KlipyMediaItemDto.Ad)
        assertTrue(data[3] is KlipyMediaItemDto.General)
    }

    @Test
    fun `maps a gif item to the embed and preview renditions`() {
        val ui =
            decode(MIXED_PAGE)
                .data
                ?.data
                .orEmpty()
                .mapNotNull { it.toUiOrNull() }

        val cat = ui.first { it.slug == "cat" }
        assertEquals(KlipyMediaType.GIF, cat.type)
        // Embed = highest gif (hd), keeping the static.klipy.com URL + pixel dims.
        assertEquals("https://static.klipy.com/ii/hd/cat.gif", cat.embedUrl)
        assertEquals(480, cat.embedWidth)
        assertEquals(360, cat.embedHeight)
        assertEquals("https://static.klipy.com/ii/hd/cat.mp4", cat.mp4Url)
        // Preview = lightest webp (sm).
        assertEquals("https://static.klipy.com/ii/sm/cat.webp", cat.previewUrl)
        assertEquals(160, cat.previewWidth)
        assertEquals("AAAA", cat.blurPreview)
    }

    @Test
    fun `sticker type maps to STICKER and falls back to the embed gif for preview`() {
        val star =
            decode(MIXED_PAGE)
                .data
                ?.data
                .orEmpty()
                .mapNotNull { it.toUiOrNull() }
                .first { it.slug == "star" }

        assertEquals(KlipyMediaType.STICKER, star.type)
        // No sm/xs webp rendition → preview falls back to the embed gif.
        assertEquals("https://static.klipy.com/ii/hd/star.gif", star.previewUrl)
    }

    @Test
    fun `ad items are dropped`() {
        val slugs =
            decode(MIXED_PAGE)
                .data
                ?.data
                .orEmpty()
                .mapNotNull { it.toUiOrNull() }
                .map { it.slug }

        assertTrue(slugs.none { it.startsWith("ad") })
        assertEquals(setOf("cat", "star"), slugs.toSet())
    }

    @Test
    fun `general item without a gif rendition is unusable and dropped`() {
        // The "webp-only" item in the page has file.hd.webp but no gif.
        val webpOnly =
            decode(MIXED_PAGE)
                .data
                ?.data
                .orEmpty()
                .filterIsInstance<KlipyMediaItemDto.General>()
                .first { it.slug == "webp-only" }

        assertNull(webpOnly.toUiOrNull())
    }

    @Test
    fun `preview rendition with a null url falls back to the embed gif for url and dimensions`() {
        // sm.webp exists but its url is null → must not be picked (would keep 999x111
        // dims while sourcing the image from the embed gif, distorting the cell).
        val payload =
            """
            {
              "data": { "has_next": false, "data": [
                {
                  "type": "gif", "slug": "nullurl",
                  "file": {
                    "hd": { "gif": { "url": "https://static.klipy.com/ii/hd/nullurl.gif", "width": 400, "height": 300 } },
                    "sm": { "webp": { "url": null, "width": 999, "height": 111 } }
                  }
                }
              ] }
            }
            """.trimIndent()

        val ui =
            decode(payload)
                .data
                ?.data
                .orEmpty()
                .mapNotNull { it.toUiOrNull() }
                .single()

        assertEquals("https://static.klipy.com/ii/hd/nullurl.gif", ui.previewUrl)
        assertEquals(400, ui.previewWidth)
        assertEquals(300, ui.previewHeight)
    }

    private fun decode(payload: String): KlipyMediaResponseDto = json.decodeFromString(KlipyMediaResponseDto.serializer(), payload)

    private companion object {
        val MIXED_PAGE =
            """
            {
              "result": true,
              "data": {
                "has_next": true,
                "data": [
                  {
                    "type": "gif", "slug": "cat", "title": "Cat", "blur_preview": "AAAA",
                    "file": {
                      "hd": {
                        "gif":  { "url": "https://static.klipy.com/ii/hd/cat.gif",  "width": 480, "height": 360 },
                        "mp4":  { "url": "https://static.klipy.com/ii/hd/cat.mp4",  "width": 480, "height": 360 },
                        "webp": { "url": "https://static.klipy.com/ii/hd/cat.webp", "width": 480, "height": 360 }
                      },
                      "sm": { "webp": { "url": "https://static.klipy.com/ii/sm/cat.webp", "width": 160, "height": 120 } }
                    }
                  },
                  {
                    "type": "sticker", "slug": "star",
                    "file": { "hd": { "gif": { "url": "https://static.klipy.com/ii/hd/star.gif", "width": 200, "height": 200 } } }
                  },
                  { "type": "ad", "content": "https://ads.example/x", "width": 300, "height": 250 },
                  {
                    "type": "gif", "slug": "webp-only",
                    "file": { "hd": { "webp": { "url": "https://static.klipy.com/ii/hd/webp-only.webp", "width": 300, "height": 300 } } }
                  }
                ]
              }
            }
            """.trimIndent()
    }
}
