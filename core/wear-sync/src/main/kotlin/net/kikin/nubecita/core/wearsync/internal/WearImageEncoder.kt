package net.kikin.nubecita.core.wearsync.internal

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Turns an image URL into small watch-sized bytes for a Data Layer `Asset`. */
internal interface WearImageEncoder {
    /** WebP bytes no larger than [maxPx] on the long edge, or null when the image can't be loaded. */
    suspend fun encode(
        url: String,
        maxPx: Int,
    ): ByteArray?

    companion object {
        /** Post thumbnails: about the watch's screen width (the spike used 320 px). */
        const val THUMBNAIL_PX = 320

        /** Author avatars render at ~24–32 dp on the watch. */
        const val AVATAR_PX = 96
    }
}

/**
 * Decodes through the app's shared Coil [ImageLoader] (so it reuses Coil's
 * disk cache and any image the app already fetched) and re-encodes as lossy
 * WebP — photos compress to ~10–15 KB at thumbnail size.
 *
 * The loader comes from [SingletonImageLoader], not constructor injection:
 * `:app`'s `CoilModule` is the only `ImageLoader` binding, so injecting it
 * broke the Hilt test graph of every module that includes this one
 * (chats, settings, widget-sync androidTest). `NubecitaApplication`
 * supplies the Hilt-built loader as the singleton, so the app gets the same
 * instance either way.
 */
internal class CoilWearImageEncoder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WearImageEncoder {
        private val imageLoader: ImageLoader get() = SingletonImageLoader.get(context)

        override suspend fun encode(
            url: String,
            maxPx: Int,
        ): ByteArray? {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(url)
                    .size(Size(maxPx, maxPx))
                    // Software bitmap: hardware bitmaps can't be compressed.
                    .allowHardware(false)
                    .build()
            return try {
                val result = imageLoader.execute(request) as? SuccessResult ?: return null
                val bitmap = result.image.toBitmap()
                ByteArrayOutputStream().use { out ->
                    bitmap.compress(webpLossy(), WEBP_QUALITY, out)
                    out.toByteArray()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                // Log without query params, which can carry signed tokens.
                Timber.w(error, "wear image encode failed: %s", url.substringBefore('?'))
                null
            }
        }

        private companion object {
            const val WEBP_QUALITY = 70

            // WEBP_LOSSY only exists from API 30; the phone app's minSdk is 28.
            // Below 30 the deprecated WEBP is lossy at quality < 100.
            fun webpLossy(): Bitmap.CompressFormat =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
        }
    }
