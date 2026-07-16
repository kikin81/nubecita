package net.kikin.nubecita.core.posting

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File

/**
 * Unit tests for [MediaCopy] — the pure, JVM-testable core of the shared-image
 * copy path (byte-cap copy, magic-byte image sniff, orphan sweep). No Android
 * types, so it runs on plain JVM; the Android glue ([SharedMediaStore]) that
 * wires `Context`/`Uri`/`ContentResolver` around it is covered by instrumented
 * tests.
 *
 * The security-relevant assertions (Decision 5 §3–4): oversize input is
 * rejected — not truncated — and only a header-confirmed decodable image passes.
 */
class MediaCopyTest {
    // --- sniff: magic-byte image identification ------------------------------

    @Test
    fun sniff_pngHeader_returnsPng() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0)
        assertEquals(MediaCopy.ImageType.PNG, MediaCopy.sniff(png))
    }

    @Test
    fun sniff_jpegHeader_returnsJpeg() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0, 0, 0)
        assertEquals(MediaCopy.ImageType.JPEG, MediaCopy.sniff(jpeg))
    }

    @Test
    fun sniff_gif87aHeader_returnsGif() {
        assertEquals(MediaCopy.ImageType.GIF, MediaCopy.sniff("GIF87a....".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun sniff_gif89aHeader_returnsGif() {
        assertEquals(MediaCopy.ImageType.GIF, MediaCopy.sniff("GIF89a....".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun sniff_webpHeader_returnsWebp() {
        // RIFF....WEBP — bytes 0-3 "RIFF", 8-11 "WEBP".
        val webp =
            "RIFF".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x24, 0, 0, 0) +
                "WEBP".toByteArray(Charsets.US_ASCII) +
                "VP8 ".toByteArray(Charsets.US_ASCII)
        assertEquals(MediaCopy.ImageType.WEBP, MediaCopy.sniff(webp))
    }

    @Test
    fun sniff_riffThatIsNotWebp_returnsNull() {
        // RIFF container but a WAV/AVI, not WEBP — must not be accepted as an image.
        val wav =
            "RIFF".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x24, 0, 0, 0) +
                "WAVE".toByteArray(Charsets.US_ASCII) +
                "fmt ".toByteArray(Charsets.US_ASCII)
        assertNull(MediaCopy.sniff(wav))
    }

    @Test
    fun sniff_nonImageBytes_returnsNull() {
        assertNull(MediaCopy.sniff("not an image at all".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun sniff_emptyOrTooShort_returnsNull() {
        assertNull(MediaCopy.sniff(ByteArray(0)))
        assertNull(MediaCopy.sniff(byteArrayOf(0x89.toByte(), 0x50))) // 2 bytes: too short even for PNG
    }

    // --- copyBounded: hard byte ceiling --------------------------------------

    @Test
    fun copyBounded_underCap_copiesFullyAndReturnsTrue(
        @TempDir dir: File,
    ) {
        val payload = ByteArray(500) { (it % 251).toByte() }
        val dest = File(dir, "out.bin")
        val ok = MediaCopy.copyBounded(ByteArrayInputStream(payload), dest, maxBytes = 1_000)
        assertTrue(ok)
        assertArrayEquals(payload, dest.readBytes())
    }

    @Test
    fun copyBounded_exactlyAtCap_isOk(
        @TempDir dir: File,
    ) {
        val payload = ByteArray(1_000) { 1 }
        val dest = File(dir, "out.bin")
        assertTrue(MediaCopy.copyBounded(ByteArrayInputStream(payload), dest, maxBytes = 1_000))
        assertEquals(1_000, dest.length().toInt())
    }

    @Test
    fun copyBounded_overCap_returnsFalseAndDeletesDest(
        @TempDir dir: File,
    ) {
        val payload = ByteArray(1_001) { 1 }
        val dest = File(dir, "out.bin")
        val ok = MediaCopy.copyBounded(ByteArrayInputStream(payload), dest, maxBytes = 1_000)
        assertFalse(ok)
        assertFalse(dest.exists()) // rejected, not truncated: no partial file left behind
    }

    // --- sweep: orphan age boundary ------------------------------------------

    @Test
    fun sweep_deletesFilesOlderThanMaxAge(
        @TempDir dir: File,
    ) {
        val now = 1_000_000_000L
        val maxAge = 24L * 60 * 60 * 1000 // 24h
        val old =
            File(dir, "old.jpg").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(now - maxAge - 1)
            }
        val fresh =
            File(dir, "fresh.jpg").apply {
                writeBytes(byteArrayOf(1))
                setLastModified(now - 1)
            }

        val deleted = MediaCopy.sweep(dir, now = now, maxAgeMillis = maxAge)

        assertEquals(1, deleted)
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }

    @Test
    fun sweep_missingDir_returnsZeroWithoutCrashing() {
        assertEquals(0, MediaCopy.sweep(File("/nonexistent/composer_shares"), now = 1L, maxAgeMillis = 1L))
    }
}
