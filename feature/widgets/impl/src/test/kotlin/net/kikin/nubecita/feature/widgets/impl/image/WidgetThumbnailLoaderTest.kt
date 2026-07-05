package net.kikin.nubecita.feature.widgets.impl.image

import android.graphics.Bitmap
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
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
        decodeBitmapFile = { path ->
            decodedPaths += path
            bitmap
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
    fun `text-only embed never reaches the decoder`() =
        runTest {
            val decoder = RecordingDecoder(succeed = true)

            val result = loader(decoder).load("did:plc:a", "at://post/1", EmbedUi.Empty)

            assertNull(result)
            assertTrue(decoder.calls.isEmpty())
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
