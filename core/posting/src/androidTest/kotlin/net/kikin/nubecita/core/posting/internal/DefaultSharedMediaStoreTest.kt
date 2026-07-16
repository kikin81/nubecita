package net.kikin.nubecita.core.posting.internal

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [DefaultSharedMediaStore] — the real `Context` /
 * `ContentResolver` / `Uri` glue that the JVM-unit-tested [net.kikin.nubecita.core.posting.MediaCopy]
 * pure core cannot exercise. Runs against the instrumentation target context's
 * real `filesDir`, so it covers the security-relevant behavior end to end:
 * own-authority rejection, magic-byte rejection of non-images, the byte cap,
 * the owned-copy guards on resolve/delete, and the orphan sweep.
 *
 * The impl is constructed directly (no Hilt) with an injected tiny byte cap so
 * the over-cap boundary is testable without materializing a 25 MB source.
 *
 * These live in `:core:posting`'s androidTest, which CI compile-checks but does
 * not run (CI's instrumented job runs `:app` production only); they run locally
 * via `connectedProductionDebugAndroidTest` and in the nightly instrumented job.
 */
@RunWith(AndroidJUnit4::class)
class DefaultSharedMediaStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sharesDir get() = File(context.filesDir, "composer_shares")

    // A byte payload whose header is a valid PNG signature. The store sniffs magic
    // bytes (it never decodes), so the signature + filler is a sufficient "image".
    private val pngPayload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(40)
    private val notImagePayload = "this is definitely not an image file".toByteArray()

    private fun store(
        maxImageBytes: Long = 25L * 1024 * 1024,
        maxAgeMillis: Long = 24L * 60 * 60 * 1000,
    ) = DefaultSharedMediaStore(context, Dispatchers.Unconfined, maxImageBytes, maxAgeMillis)

    /** Write [bytes] to a cache file and return a `file://` Uri (the store reads via ContentResolver, which supports file://). */
    private fun sourceUri(
        bytes: ByteArray,
        name: String = "src.bin",
    ): Uri {
        val f = File(context.cacheDir, name).apply { writeBytes(bytes) }
        return Uri.fromFile(f)
    }

    @Before
    fun clean() {
        sharesDir.deleteRecursively()
    }

    @After
    fun cleanup() {
        sharesDir.deleteRecursively()
        listOf("src.bin", "outside.png", "keep.png").forEach { File(context.cacheDir, it).delete() }
    }

    @Test
    fun copyIn_validImage_createsAppOwnedCopy() =
        runBlocking {
            val copied = store().copyIn(sourceUri(pngPayload))

            assertNotNull("expected an app-owned copy", copied)
            val file = File(copied!!.path!!)
            assertEquals("file", copied.scheme)
            assertTrue("copy should exist", file.exists())
            assertEquals("copy should live under composer_shares", sharesDir.canonicalPath, file.parentFile?.canonicalPath)
            assertEquals("png", file.extension)
            assertArrayEquals(pngPayload, file.readBytes())
            // No temp files left behind after a successful copy.
            assertTrue(sharesDir.listFiles().orEmpty().none { it.name.startsWith("tmp_") })
        }

    @Test
    fun copyIn_nonImageBytes_rejectedAndCleansUp() =
        runBlocking {
            val copied = store().copyIn(sourceUri(notImagePayload))

            assertNull("non-image bytes must be rejected", copied)
            // Directory has no leftover copy or temp file.
            assertTrue((sharesDir.listFiles() ?: emptyArray()).isEmpty())
        }

    @Test
    fun copyIn_overByteCap_rejectedAndCleansUp() =
        runBlocking {
            // 48-byte PNG payload against a 20-byte cap → rejected, not truncated.
            val copied = store(maxImageBytes = 20).copyIn(sourceUri(pngPayload))

            assertNull("over-cap source must be rejected", copied)
            assertTrue((sharesDir.listFiles() ?: emptyArray()).isEmpty())
        }

    @Test
    fun copyIn_ownAuthorityContentUri_rejected() =
        runBlocking {
            // Confused-deputy: a content:// pointing at one of our own providers.
            val hostile = Uri.parse("content://${context.packageName}.fileprovider/secret")
            assertNull(store().copyIn(hostile))
        }

    @Test
    fun attachmentFor_copiedUri_resolvesWithSniffedMime() =
        runBlocking {
            val copied = store().copyIn(sourceUri(pngPayload))!!

            val attachment = store().attachmentFor(copied.toString())

            assertNotNull(attachment)
            assertEquals(copied, attachment!!.uri)
            assertEquals("image/png", attachment.mimeType)
        }

    @Test
    fun attachmentFor_missingCopy_returnsNull() =
        runBlocking {
            sharesDir.mkdirs()
            val gone = Uri.fromFile(File(sharesDir, "does-not-exist.png")).toString()
            assertNull(store().attachmentFor(gone))
        }

    @Test
    fun attachmentFor_pathOutsideSharesDir_returnsNull() =
        runBlocking {
            // A real file, but not under composer_shares → not one of ours.
            val outside = File(context.cacheDir, "outside.png").apply { writeBytes(pngPayload) }
            assertNull(store().attachmentFor(Uri.fromFile(outside).toString()))
        }

    @Test
    fun delete_ownedCopy_removesIt() =
        runBlocking {
            val copied = store().copyIn(sourceUri(pngPayload))!!
            assertTrue(File(copied.path!!).exists())

            store().delete(copied)

            assertFalse(File(copied.path!!).exists())
        }

    @Test
    fun delete_pathOutsideSharesDir_isNoop() =
        runBlocking {
            val outside = File(context.cacheDir, "keep.png").apply { writeBytes(pngPayload) }

            store().delete(Uri.fromFile(outside))

            assertTrue("must not delete files outside composer_shares", outside.exists())
        }

    @Test
    fun sweepOrphans_deletesOnlyExpiredCopies() =
        runBlocking {
            sharesDir.mkdirs()
            val old = File(sharesDir, "old.png").apply { writeBytes(pngPayload) }
            val fresh = File(sharesDir, "fresh.png").apply { writeBytes(pngPayload) }
            // setLastModified can fail silently on some emulator filesystems — assert the precondition held.
            assertTrue(
                "failed to backdate the orphan's mtime",
                old.setLastModified(System.currentTimeMillis() - 25L * 60 * 60 * 1000), // 25h > 24h retention
            )

            store().sweepOrphans()

            assertFalse("expired copy should be swept", old.exists())
            assertTrue("recent copy should survive", fresh.exists())
        }
}
