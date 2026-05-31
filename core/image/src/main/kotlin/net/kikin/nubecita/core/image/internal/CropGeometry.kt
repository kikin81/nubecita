package net.kikin.nubecita.core.image.internal

import kotlin.math.max
import kotlin.math.roundToInt

/** A pixel rectangle in working-bitmap space, ready for `Bitmap.createBitmap`. */
internal data class CropRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * The pure geometry behind the fixed-frame crop. No Android or Compose
 * types — fully JVM-unit-testable (see `CropGeometryTest`).
 *
 * Model: a fixed crop frame ([frameW] × [frameH] screen px) stays centered;
 * the user pans ([offsetX]/[offsetY] screen px) and zooms ([scale]) the
 * image behind it. At `scale == 1` the image is "cover-fit" — scaled by
 * [baseScale] so it exactly covers the frame on the tight axis — and it can
 * only be zoomed in from there ([MIN_SCALE]). The offset is constrained so
 * the image always covers the frame ([clampOffset]).
 *
 * [sourceRect] inverts that transform to the pixel rectangle of the
 * (already EXIF-oriented) working bitmap under the frame. EXIF orientation
 * is applied upstream by `ImageDecoder`, so this math only ever sees
 * oriented dimensions.
 */
internal object CropGeometry {
    /** The minimum zoom: cover-fit. Zooming out past this would expose frame edges. */
    const val MIN_SCALE = 1f

    /**
     * The crop frame size (screen px) for a [canvasW] × [canvasH] surface and
     * a frame [aspect] (width / height): the largest rect of that aspect that
     * fits within the canvas shrunk by [inset], centered. Shared by the live
     * Canvas and the extractor so the displayed frame and the extracted region
     * use identical dimensions.
     */
    fun frameSize(
        canvasW: Float,
        canvasH: Float,
        aspect: Float,
        inset: Float,
    ): Pair<Float, Float> {
        val availW = canvasW * inset
        val availH = canvasH * inset
        var frameW = availW
        var frameH = frameW / aspect
        if (frameH > availH) {
            frameH = availH
            frameW = frameH * aspect
        }
        return frameW to frameH
    }

    /** Scale at which the image exactly covers the frame on its tight axis. */
    fun baseScale(
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
    ): Float = max(frameW / imageW, frameH / imageH)

    fun clampScale(
        scale: Float,
        maxScale: Float,
    ): Float = scale.coerceIn(MIN_SCALE, maxScale)

    fun maxOffsetX(
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
        scale: Float,
    ): Float = ((displayWidth(imageW, imageH, frameW, frameH, scale) - frameW) / 2f).coerceAtLeast(0f)

    fun maxOffsetY(
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
        scale: Float,
    ): Float = ((displayHeight(imageW, imageH, frameW, frameH, scale) - frameH) / 2f).coerceAtLeast(0f)

    /** Constrains [offsetX]/[offsetY] so the scaled image still covers the frame. */
    fun clampOffset(
        offsetX: Float,
        offsetY: Float,
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
        scale: Float,
    ): Pair<Float, Float> {
        val mx = maxOffsetX(imageW, imageH, frameW, frameH, scale)
        val my = maxOffsetY(imageW, imageH, frameW, frameH, scale)
        return offsetX.coerceIn(-mx, mx) to offsetY.coerceIn(-my, my)
    }

    /**
     * The pixel rectangle of the working bitmap currently framed. Defensive:
     * the offset is re-clamped and the scale floored here, and the result is
     * coerced in-bounds and to ≥ 1 px, so a bad caller can't produce an
     * out-of-bounds or empty `createBitmap` region.
     */
    fun sourceRect(
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
        scale: Float,
        offsetX: Float,
        offsetY: Float,
    ): CropRect {
        val s = max(scale, MIN_SCALE)
        val k = baseScale(imageW, imageH, frameW, frameH) * s
        val dispW = imageW * k
        val dispH = imageH * k
        val (cx, cy) = clampOffset(offsetX, offsetY, imageW, imageH, frameW, frameH, s)

        val leftF = (dispW / 2f - frameW / 2f - cx) / k
        val topF = (dispH / 2f - frameH / 2f - cy) / k
        val widthF = frameW / k
        val heightF = frameH / k

        // Coerce left/top into [0, dim-1] and right/bottom into [left+1, dim]
        // so the region is always in-bounds and at least one pixel.
        val left = leftF.roundToInt().coerceIn(0, (imageW - 1).coerceAtLeast(0))
        val top = topF.roundToInt().coerceIn(0, (imageH - 1).coerceAtLeast(0))
        val right = (leftF + widthF).roundToInt().coerceIn(left + 1, imageW)
        val bottom = (topF + heightF).roundToInt().coerceIn(top + 1, imageH)
        return CropRect(left = left, top = top, right = right, bottom = bottom)
    }

    private fun displayWidth(
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
        scale: Float,
    ): Float = imageW * baseScale(imageW, imageH, frameW, frameH) * max(scale, MIN_SCALE)

    private fun displayHeight(
        imageW: Int,
        imageH: Int,
        frameW: Float,
        frameH: Float,
        scale: Float,
    ): Float = imageH * baseScale(imageW, imageH, frameW, frameH) * max(scale, MIN_SCALE)
}
