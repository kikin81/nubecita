package net.kikin.nubecita.feature.widgets.impl.image

import android.graphics.Bitmap
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Render-time thumbnail resolution incl. the local-cache self-heal
 * (nubecita-iqpc): the feed cache can be fresher than the thumbnail store
 * (foreground app writes the cache; only the background worker prefetches), so
 * a missing file falls back to a LOCAL-ONLY decode written through the store.
 */
internal class WidgetThumbnailLoaderTest {
    @TempDir
    lateinit var tempFolder: File

    private val bitmap = mockk<Bitmap>()
    private val decodedPaths = mutableListOf<String>()

    private fun loader(
        decoder: RecordingDecoder,
        store: WidgetThumbnailStore = WidgetThumbnailStore(tempFolder),
    ) = WidgetThumbnailLoader(
        store = store,
        decoder = decoder,
        dispatcher = UnconfinedTestDispatcher(),
        decodeBitmapFile = { path ->
            decodedPaths += path
            // A file holding CORRUPT_BYTES simulates an undecodable JPEG
            // (BitmapFactory.decodeFile returns null on garbage input).
            if (File(path).readBytes().contentEquals(CORRUPT_BYTES)) null else bitmap
        },
    )

    private fun imagesEmbed(url: String = "https://cdn.test/thumb.jpg") =
        EmbedUi.Images(
            items = persistentListOf(ImageUi(fullsizeUrl = "full", thumbUrl = url, altText = null, aspectRatio = null)),
        )

    @Test
    fun `prefetched thumbnail decodes straight from the store without touching the decoder`() =
        runTest {
            val store = WidgetThumbnailStore(tempFolder)
            store.thumbnailFile("did:plc:a", "at://post/1").writeBytes(byteArrayOf(1))
            val decoder = RecordingDecoder(succeed = true)

            val result = loader(decoder, store).load("did:plc:a", "at://post/1", imagesEmbed())

            assertSame(bitmap, result)
            assertTrue(decoder.calls.isEmpty(), "an existing file must not trigger any decode")
        }

    @Test
    fun `missing thumbnail self-heals from the local cache and persists through the store`() =
        runTest {
            val decoder = RecordingDecoder(succeed = true)
            val store = WidgetThumbnailStore(tempFolder)

            val result = loader(decoder, store).load("did:plc:a", "at://post/1", imagesEmbed("https://cdn.test/x.jpg"))

            assertSame(bitmap, result)
            assertEquals(1, decoder.calls.size)
            val call = decoder.calls.single()
            assertEquals("https://cdn.test/x.jpg", call.url)
            assertEquals(store.thumbnailFile("did:plc:a", "at://post/1").path, call.dest.path)
            assertEquals(false, call.allowNetwork, "the render path must never issue network")
        }

    @Test
    fun `self-heal miss renders text-only without a second attempt`() =
        runTest {
            val decoder = RecordingDecoder(succeed = false)

            val result = loader(decoder).load("did:plc:a", "at://post/1", imagesEmbed())

            assertNull(result)
            assertTrue(decodedPaths.isEmpty(), "no bitmap decode when the heal produced no file")
        }

    @Test
    fun `corrupted store file is deleted and healed instead of rendering empty forever`() =
        runTest {
            val store = WidgetThumbnailStore(tempFolder)
            store.thumbnailFile("did:plc:a", "at://post/1").writeBytes(CORRUPT_BYTES)
            val decoder = RecordingDecoder(succeed = true)

            val result = loader(decoder, store).load("did:plc:a", "at://post/1", imagesEmbed())

            assertSame(bitmap, result, "the heal must replace the corrupted file and decode it")
            assertEquals(1, decoder.calls.size, "the corrupted file must fall through to the self-heal")
        }

    @Test
    fun `corrupted store file with a failing heal renders text-only and clears the bad file`() =
        runTest {
            val store = WidgetThumbnailStore(tempFolder)
            val file = store.thumbnailFile("did:plc:a", "at://post/1")
            file.writeBytes(CORRUPT_BYTES)
            val decoder = RecordingDecoder(succeed = false)

            val result = loader(decoder, store).load("did:plc:a", "at://post/1", imagesEmbed())

            assertNull(result)
            assertTrue(!file.exists(), "the undecodable file must not survive to poison future renders")
        }

    @Test
    fun `text-only embed never reaches the decoder`() =
        runTest {
            val decoder = RecordingDecoder(succeed = true)

            val result = loader(decoder).load("did:plc:a", "at://post/1", EmbedUi.Empty)

            assertNull(result)
            assertTrue(decoder.calls.isEmpty())
        }

    private companion object {
        val CORRUPT_BYTES = byteArrayOf(0)
    }

    private class RecordingDecoder(
        private val succeed: Boolean,
    ) : ThumbnailDecoder {
        data class Call(
            val url: String,
            val dest: File,
            val allowNetwork: Boolean,
        )

        val calls = mutableListOf<Call>()

        override suspend fun decodeToFile(
            url: String,
            dest: File,
            allowNetwork: Boolean,
        ): Boolean {
            calls += Call(url, dest, allowNetwork)
            if (succeed) {
                dest.parentFile?.mkdirs()
                dest.writeBytes(byteArrayOf(1))
            }
            return succeed
        }
    }
}
