package net.kikin.nubecita.feature.widgets.impl.image

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * Decodes a remote image URL to a bounded JPEG file. The only Android/Coil-bound
 * step of the prefetch pipeline — split behind this seam so
 * [GlanceWidgetImagePrefetcher]'s orchestration stays JVM-unit-testable with a
 * fake, and the real Coil path is exercised by the instrumented budget test
 * (the C analogue of A's scale test).
 */
internal interface ThumbnailDecoder {
    /**
     * Decode [url] to a bitmap downscaled to fit [MAX_THUMB_PX]² and write it to
     * [dest] as JPEG. Returns whether a usable thumbnail was written. Never
     * throws on a decode/IO failure — a missing thumbnail just renders text-only.
     */
    suspend fun decodeToFile(
        url: String,
        dest: File,
    ): Boolean
}

/**
 * Coil-backed [ThumbnailDecoder]. Uses the app's configured [ImageLoader] (from
 * `:app`'s `CoilModule`, so the disk/memory cache + OkHttp fetcher are shared)
 * but decodes to a software bitmap (`allowHardware(false)`) bounded to a single
 * fixed box — the background prefetch can't know the active responsive cell
 * size (D-C5), so it decodes to the largest a layout could need and lets Glance
 * scale down at render.
 */
internal class CoilThumbnailDecoder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
    ) : ThumbnailDecoder {
        override suspend fun decodeToFile(
            url: String,
            dest: File,
        ): Boolean {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(Size(MAX_THUMB_PX, MAX_THUMB_PX))
                    // Software bitmap so it can be compressed to a file (hardware
                    // bitmaps can't be read back).
                    .allowHardware(false)
                    .build()

            val bitmap =
                (imageLoader.execute(request) as? SuccessResult)?.image?.toBitmap()
                    ?: return false

            return try {
                dest.parentFile?.mkdirs()
                dest.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                true
            } catch (io: IOException) {
                Timber.tag(TAG).w(io, "failed writing widget thumbnail: %s", dest.name)
                dest.delete()
                false
            }
        }

        private companion object {
            const val TAG = "WidgetThumbDecoder"

            /**
             * Fixed decode bounding box (px). ~600px covers the largest thumbnail a
             * responsive widget layout needs (≈180–200dp at xxhdpi); Glance scales
             * it down per cell. Tunable against the instrumented budget test.
             */
            const val MAX_THUMB_PX = 600

            const val JPEG_QUALITY = 85
        }
    }
