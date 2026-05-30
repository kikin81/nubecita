package net.kikin.nubecita.core.image.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import net.kikin.nubecita.core.image.BLUESKY_BLOB_LIMIT_BYTES
import net.kikin.nubecita.core.image.CropShape
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Decode + extraction half of the crop (the Android-graphics side; the
 * geometry lives in [CropGeometry]).
 *
 * Split out from the composable so it has no Compose dependency and the
 * pipeline reads top-to-bottom: decode an EXIF-oriented working bitmap →
 * invert the frame transform to a pixel rect → `createBitmap` that region →
 * WebP-compress under the blob cap. `ImageDecoder` applies EXIF orientation
 * during decode, so [CropGeometry] only ever sees oriented dimensions and
 * the extracted region needs no rotation fix-up (unlike `BitmapRegionDecoder`,
 * which ignores EXIF — the reason we decode-then-`createBitmap` instead).
 */
@RequiresApi(Build.VERSION_CODES.P)
internal object CropExtractor {
    /** Cap on the decoded working bitmap's longest side — display + crop source. */
    const val WORKING_MAX_DIM = 2048

    /**
     * Decode [uri] to an EXIF-oriented [Bitmap] capped at [WORKING_MAX_DIM] on
     * its longest side. Software allocator because the result is later read by
     * `Bitmap.createBitmap` (a hardware bitmap can't be a `createBitmap` source).
     */
    fun decodeWorkingBitmap(
        context: Context,
        uri: Uri,
        maxDim: Int = WORKING_MAX_DIM,
    ): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > maxDim) {
                val scale = maxDim.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).toInt().coerceAtLeast(1),
                    (info.size.height * scale).toInt().coerceAtLeast(1),
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }
    }

    /**
     * Crop [working] to the region currently under the frame and compress it
     * to WebP under [maxBytes]. [canvasW]/[canvasH] are the crop surface size
     * (px); [scale]/[offset] are the live gesture state in the same screen-px
     * space; [frameInset] matches the displayed frame. Returns `(bytes, mime)`.
     */
    fun extract(
        working: Bitmap,
        shape: CropShape,
        canvasW: Float,
        canvasH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
        frameInset: Float,
        maxBytes: Long = BLUESKY_BLOB_LIMIT_BYTES,
    ): Pair<ByteArray, String> {
        val (frameW, frameH) = CropGeometry.frameSize(canvasW, canvasH, shape.aspect, frameInset)
        val rect =
            CropGeometry.sourceRect(
                imageW = working.width,
                imageH = working.height,
                frameW = frameW,
                frameH = frameH,
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
            )
        val cropped =
            Bitmap.createBitmap(working, rect.left, rect.top, rect.width, rect.height)
        try {
            return compressUnderCap(cropped, maxBytes) to OUTPUT_MIME
        } finally {
            if (cropped != working) cropped.recycle()
        }
    }

    /**
     * WebP-compress [bitmap] at descending quality until it fits under
     * [maxBytes], or at the lowest quality as a last resort. A cropped
     * working-bitmap region (≤ [WORKING_MAX_DIM] px) compresses well under
     * the 1 MB cap at high quality, so this is the cheap counterpart to
     * `BitmapImageEncoder`'s full decode-and-downsample ladder.
     */
    private fun compressUnderCap(
        bitmap: Bitmap,
        maxBytes: Long,
    ): ByteArray {
        var last = ByteArray(0)
        for (quality in QUALITY_LADDER) {
            last = compress(bitmap, quality)
            if (last.size <= maxBytes) return last
        }
        return last
    }

    private fun compress(
        bitmap: Bitmap,
        quality: Int,
    ): ByteArray {
        val out = ByteArrayOutputStream(INITIAL_BUFFER_BYTES)
        val format =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        bitmap.compress(format, quality, out)
        return out.toByteArray()
    }

    private const val OUTPUT_MIME = "image/webp"
    private val QUALITY_LADDER = listOf(92, 85, 75, 65, 55, 45)
    private const val INITIAL_BUFFER_BYTES = 256 * 1024
}
