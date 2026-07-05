package net.kikin.nubecita.feature.widgets.impl.image

import android.content.Context
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
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
     *
     * With [allowNetwork] = false the decode is restricted to Coil's local
     * memory/disk caches — the render-time self-heal path (nubecita-iqpc) must
     * never issue network from a Glance session.
     */
    suspend fun decodeToFile(
        url: String,
        dest: File,
        allowNetwork: Boolean = true,
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
        @param:ApplicationContext private val context: Context,
        private val imageLoader: ImageLoader,
    ) : ThumbnailDecoder {
        override suspend fun decodeToFile(
            url: String,
            dest: File,
            allowNetwork: Boolean,
        ): Boolean {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(Size(MAX_THUMB_PX, MAX_THUMB_PX))
                    // Software bitmap so it can be compressed to a file (hardware
                    // bitmaps can't be read back).
                    .allowHardware(false)
                    .apply {
                        // Local-only mode (render-time self-heal): serve from Coil's
                        // memory/disk caches, never the network.
                        if (!allowNetwork) networkCachePolicy(CachePolicy.DISABLED)
                    }.build()

            val result = imageLoader.execute(request)
            if (result !is SuccessResult) {
                // Surface why; this path was previously silent, hiding intermittent
                // misses. Log the URL without query params (avoid leaking any
                // signed-URL tokens). (ImageResult doesn't smart-cast to ErrorResult
                // here, so the explicit cast stays.) A local-only miss is EXPECTED
                // control flow (the image was simply never cached in-app) — debug
                // level, or it would flood the breadcrumb buffer on every render.
                if (allowNetwork) {
                    Timber.tag(TAG).w((result as? ErrorResult)?.throwable, "widget thumbnail decode failed: %s", url.substringBefore('?'))
                } else {
                    Timber.tag(TAG).d("widget thumbnail not in local cache: %s", url.substringBefore('?'))
                }
                return false
            }
            val bitmap = result.image.toBitmap()

            val parent = dest.parentFile ?: return false
            parent.mkdirs()
            // Write to a temp file then rename: an interrupted write (crash, process
            // death, low battery) must never leave a half-JPEG at `dest` — hasThumbnail
            // only checks existence, so a partial file would be treated as valid and
            // permanently skipped, and the Glance render thread would read garbage.
            // rename(2) publishes the fully-written file atomically.
            val tmp = File(parent, "${dest.name}.tmp")
            return try {
                val compressed =
                    tmp.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                    }
                if (compressed && tmp.renameTo(dest)) {
                    true
                } else {
                    tmp.delete()
                    false
                }
            } catch (io: IOException) {
                Timber.tag(TAG).w(io, "failed writing widget thumbnail: %s", dest.name)
                tmp.delete()
                false
            }
        }

        private companion object {
            const val TAG = "WidgetThumbDecoder"

            /**
             * Fixed decode bounding box (px), right-sized for the 68dp thumbnail
             * slot (≈272px at xxxhdpi) with headroom. Far smaller than the original
             * 600 → ~3× smaller stored JPEGs and RemoteViews IPC payload, no visible
             * loss. Tunable against the instrumented budget test.
             */
            const val MAX_THUMB_PX = 320

            const val JPEG_QUALITY = 85
        }
    }
