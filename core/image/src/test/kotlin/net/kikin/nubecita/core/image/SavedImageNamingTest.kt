package net.kikin.nubecita.core.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the pure halves of the save path — format sniffing and
 * display-name construction. The `MediaStore` glue around them is covered by
 * `DefaultImageSaverTest` in `androidTest`.
 */
internal class SavedImageNamingTest {
    @Test
    fun `sniffs a JPEG from its SOI marker`() {
        assertEquals(SavedImageFormat.Jpeg, sniffImageFormat(jpegHeader()))
    }

    @Test
    fun `sniffs a PNG from its signature`() {
        assertEquals(SavedImageFormat.Png, sniffImageFormat(pngHeader()))
    }

    @Test
    fun `sniffs a WebP from the RIFF container's WEBP marker`() {
        assertEquals(SavedImageFormat.WebP, sniffImageFormat(webpHeader()))
    }

    @Test
    fun `a RIFF container that is not WebP is not treated as WebP`() {
        // A WAV file is also RIFF. Matching on "RIFF" alone would misfile it as
        // an image/webp, so the marker past the length field must be checked.
        val wav = "RIFF".toByteArray() + byteArrayOf(0x24, 0x08, 0x00, 0x00) + "WAVE".toByteArray()
        assertEquals(SavedImageFormat.Unknown, sniffImageFormat(wav))
    }

    @Test
    fun `unrecognised bytes fall back to the generic image type`() {
        assertEquals(SavedImageFormat.Unknown, sniffImageFormat(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)))
    }

    @Test
    fun `a truncated header does not crash and falls back`() {
        // Guards the offset arithmetic: a buffer shorter than the WebP marker
        // offset must not index out of bounds.
        assertEquals(SavedImageFormat.Unknown, sniffImageFormat("RIFF".toByteArray()))
        assertEquals(SavedImageFormat.Unknown, sniffImageFormat(ByteArray(0)))
    }

    @Test
    fun `a JPEG prefix shorter than the full signature length still sniffs`() {
        // The saver reads only SIGNATURE_LENGTH bytes and may get fewer back
        // from a short read; three bytes is enough to identify a JPEG.
        assertEquals(SavedImageFormat.Jpeg, sniffImageFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
    }

    @Test
    fun `display name carries the format's extension`() {
        assertEquals("nubecita_1723678901234.jpg", savedImageDisplayName(SavedImageFormat.Jpeg, 1723678901234L))
        assertEquals("nubecita_7.png", savedImageDisplayName(SavedImageFormat.Png, 7L))
        assertEquals("nubecita_7.webp", savedImageDisplayName(SavedImageFormat.WebP, 7L))
        assertEquals("nubecita_7.img", savedImageDisplayName(SavedImageFormat.Unknown, 7L))
    }

    @Test
    fun `every format declares a distinct extension and a non-blank mime type`() {
        val extensions = SavedImageFormat.entries.map { it.fileExtension }
        assertEquals(extensions.size, extensions.toSet().size, "extensions must be distinct: $extensions")
        SavedImageFormat.entries.forEach { format ->
            assertEquals(true, format.mimeType.startsWith("image/"), "${format.name} mime must be an image type")
        }
    }

    private fun jpegHeader() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(8)

    private fun pngHeader() = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(4)

    private fun webpHeader() = "RIFF".toByteArray() + byteArrayOf(0x24, 0x08, 0x00, 0x00) + "WEBP".toByteArray()
}
