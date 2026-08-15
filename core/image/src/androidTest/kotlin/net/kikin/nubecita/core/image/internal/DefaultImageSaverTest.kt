package net.kikin.nubecita.core.image.internal

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import coil3.ImageLoader
import coil3.disk.DiskCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.image.ImageRetrievalException
import okio.Path.Companion.toOkioPath
import okio.buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for [DefaultImageSaver] — the real `MediaStore` /
 * `ContentResolver` glue that the JVM-unit-tested pure cores
 * (`sniffImageFormat`, `savedImageDisplayName`) cannot exercise.
 *
 * Constructed directly, no Hilt, mirroring `:core:posting`'s
 * `DefaultSharedMediaStoreTest`. Every row this suite creates is tracked and
 * deleted in [tearDown] so a failing run does not litter the device gallery.
 */
@RunWith(AndroidJUnit4::class)
// Class-level rather than a per-test assume: this also keeps the class from
// being loaded on API 28 at all, which matters because it references
// MediaStore.setIncludePending — an API 29 *method*, unlike the inlined String
// constants in the production code.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class DefaultImageSaverTest {
    private lateinit var context: Context
    private lateinit var diskCache: DiskCache
    private lateinit var imageLoader: ImageLoader
    private lateinit var saver: DefaultImageSaver
    private lateinit var cacheDir: File
    private val created = mutableListOf<Uri>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir = File(context.cacheDir, "image_saver_test_${System.nanoTime()}").apply { mkdirs() }
        diskCache = DiskCache.Builder().directory(cacheDir.toOkioPath()).build()
        imageLoader = ImageLoader.Builder(context).diskCache(diskCache).build()
        saver = DefaultImageSaver(context, imageLoader, Dispatchers.IO)
    }

    @After
    fun tearDown() {
        created.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        created.clear()
        diskCache.shutdown()
        cacheDir.deleteRecursively()
    }

    @Test
    fun saves_a_cached_image_into_the_nubecita_album_with_its_bytes_intact() {
        val bytes = pngBytes()
        cache(URL, bytes)

        val uri = runBlocking { saver.saveToGallery(URL) }.getOrThrow()
        created += uri

        // RELATIVE_PATH is written with a trailing slash so the value read back
        // is identical to the value written — assert equality, not a prefix.
        assertEquals("${android.os.Environment.DIRECTORY_PICTURES}/Nubecita/", uri.stringColumn(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals("image/png", uri.stringColumn(MediaStore.Images.Media.MIME_TYPE))
        assertTrue(
            "display name should carry the sniffed extension",
            uri.stringColumn(MediaStore.Images.Media.DISPLAY_NAME).orEmpty().endsWith(".png"),
        )
        assertArrayEquals("saved bytes must be byte-identical to the source", bytes, uri.readBytes())
    }

    @Test
    fun records_the_sniffed_content_type_not_the_one_implied_by_the_url() {
        // URL says jpeg — as every Bluesky CDN fullsize URL does — but the bytes
        // are a PNG. The gallery entry must describe the bytes, not the request.
        val jpegLookingUrl = "https://cdn.example/img/feed_fullsize/plain/did/cid@jpeg"
        cache(jpegLookingUrl, pngBytes())

        val uri = runBlocking { saver.saveToGallery(jpegLookingUrl) }.getOrThrow()
        created += uri

        assertEquals("image/png", uri.stringColumn(MediaStore.Images.Media.MIME_TYPE))
    }

    @Test
    fun records_no_app_written_description() {
        // MediaStore.DESCRIPTION is readOnly and EXIF-derived, so nothing the
        // app writes lands there. Pinned so a future "helpful" DESCRIPTION
        // write is caught as the no-op it is.
        cache(URL, pngBytes())

        val uri = runBlocking { saver.saveToGallery(URL) }.getOrThrow()
        created += uri

        assertNull(uri.stringColumn(MediaStore.Images.Media.DESCRIPTION))
    }

    @Test
    fun a_cached_image_is_saved_without_any_network_request() {
        // The ImageLoader here has no network components registered at all, so
        // any fetch attempt fails outright. A successful save therefore proves
        // the bytes came from the cache — the D2 optimisation cannot silently
        // regress into the fetch fallback without turning this red.
        cache(URL, pngBytes())

        val result = runBlocking { saver.saveToGallery(URL) }

        assertTrue("expected a cache-served save, got $result", result.isSuccess)
        result.getOrNull()?.let { created += it }
    }

    @Test
    fun an_uncached_image_that_cannot_be_fetched_reports_retrieval_failure() {
        // Nothing cached and no network fetcher registered.
        val result = runBlocking { saver.saveToGallery("https://cdn.example/missing.png") }

        assertTrue(result.isFailure)
        assertTrue(
            "expected ImageRetrievalException, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is ImageRetrievalException,
        )
    }

    @Test
    fun a_retrieval_failure_creates_no_row_not_even_a_pending_one() {
        // Counted with pending entries included: a leaked IS_PENDING row is
        // invisible to the gallery but still consumes storage the user cannot
        // reclaim, so a visible-only query would pass on a leak.
        //
        // SCOPE: this covers the *pre-insert* failure path — retrieval fails,
        // so no row is ever created. It does NOT exercise the post-insert
        // cleanup (a copy that fails after the row exists); forcing that would
        // need a fault-injection seam in production code, which is not worth
        // adding for it. That path is guarded by the catch block in
        // DefaultImageSaver.writeToGallery and by the fact that the whole
        // insert-write-publish sequence is non-suspending, so cancellation
        // cannot interleave. Reviewed by inspection, not by this test.
        val before = countOwnedRowsIncludingPending()

        val result = runBlocking { saver.saveToGallery("https://cdn.example/never-cached.png") }
        assertTrue(result.isFailure)

        assertEquals("a failed save must not leave a row, pending or otherwise", before, countOwnedRowsIncludingPending())
    }

    // ---------- helpers ----------

    /** Writes [bytes] into the Coil disk cache under [url], the default key. */
    private fun cache(
        url: String,
        bytes: ByteArray,
    ) {
        val editor = requireNotNull(diskCache.openEditor(url)) { "could not open a cache editor for $url" }
        diskCache.fileSystem
            .sink(editor.data)
            .buffer()
            .use { it.write(bytes) }
        editor.commit()
    }

    private fun Uri.stringColumn(column: String): String? =
        context.contentResolver.query(this, arrayOf(column), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun Uri.readBytes(): ByteArray = requireNotNull(context.contentResolver.openInputStream(this)).use { it.readBytes() }

    /**
     * Rows this app owns in the images collection, **including** pending ones.
     * `MediaStore` scopes queries to the calling app's own entries by default,
     * so this counts only what these tests create.
     */
    private fun countOwnedRowsIncludingPending(): Int {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = MediaStore.setIncludePending(collection)
        return context.contentResolver
            .query(uri, arrayOf(MediaStore.Images.Media._ID), null, null, null)
            ?.use { it.count } ?: 0
    }

    private fun pngBytes(): ByteArray {
        // 8-byte PNG signature followed by filler; the saver copies bytes
        // verbatim and never decodes, so this need not be a decodable image.
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        return signature + ByteArray(64) { (it % 251).toByte() }
    }

    private companion object {
        const val URL = "https://cdn.example/img/feed_fullsize/plain/did:plc:test/cid@jpeg"
    }
}
