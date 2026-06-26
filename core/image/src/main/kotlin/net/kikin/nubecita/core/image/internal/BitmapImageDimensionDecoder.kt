package net.kikin.nubecita.core.image.internal

import android.graphics.BitmapFactory
import net.kikin.nubecita.core.image.ImageDimensionDecoder
import net.kikin.nubecita.core.image.ImageDimensions
import javax.inject.Inject

/**
 * Production [ImageDimensionDecoder] backed by `BitmapFactory` with
 * `inJustDecodeBounds = true` — this reads only the image header to fill
 * `outWidth`/`outHeight` and allocates **no** pixel buffer, so it is cheap
 * even for the largest source photos.
 *
 * Returns `null` when the bytes don't decode to a valid image (corrupt file,
 * unsupported format) — `outWidth`/`outHeight` come back as `-1` in that case.
 */
internal class BitmapImageDimensionDecoder
    @Inject
    constructor() : ImageDimensionDecoder {
        override fun decode(bytes: ByteArray): ImageDimensions? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            return if (options.outWidth > 0 && options.outHeight > 0) {
                ImageDimensions(width = options.outWidth, height = options.outHeight)
            } else {
                null
            }
        }
    }
