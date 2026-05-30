package net.kikin.nubecita.core.image.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure-math tests for [CropGeometry] — the frame→source-rect inverse
 * transform that turns the gesture state (scale + pan offset, in screen
 * px) plus the (EXIF-oriented) working-bitmap dimensions into the pixel
 * rectangle to extract.
 *
 * All oracle values are derived by hand from the cover-fit + containment
 * model, independent of the implementation:
 *  - baseScale = max(frameW/imageW, frameH/imageH)  (scale=1 ⇒ image
 *    exactly covers the frame on at least one axis).
 *  - dispW = imageW·baseScale·scale, dispH likewise.
 *  - normalized left = 0.5 − (frameW/2 + offsetX)/dispW, width = frameW/dispW.
 *  - source px = normalized × image dims.
 *
 * EXIF orientation is handled upstream by `ImageDecoder` (which orients
 * the working bitmap before this math runs), so the geometry is purely a
 * function of the *oriented* bitmap dimensions — see
 * [sourceRect_isDrivenByOrientedDimensions].
 */
class CropGeometryTest {
    @Test
    fun baseScale_isCoverFit() {
        // Landscape 2:1 image into a 1:1 frame → height is the tight axis.
        assertEquals(1.0f, CropGeometry.baseScale(2000, 1000, 1000f, 1000f))
        // Portrait 1:2 image into a 1:1 frame → width is the tight axis.
        assertEquals(1.0f, CropGeometry.baseScale(1000, 2000, 1000f, 1000f))
        // 2:1 image into a 3:1 banner frame → width is the tight axis.
        assertEquals(0.3f, CropGeometry.baseScale(4000, 2000, 1200f, 400f), 1e-4f)
    }

    @Test
    fun frameSize_fitsAspectWithinInsetAndCenters() {
        // Square frame on a portrait canvas → limited by width.
        val (aw, ah) = CropGeometry.frameSize(1080f, 1920f, aspect = 1f, inset = 0.9f)
        assertEquals(972f, aw, 1e-3f)
        assertEquals(972f, ah, 1e-3f)
        // 3:1 banner on the same canvas → wide, short.
        val (bw, bh) = CropGeometry.frameSize(1080f, 1920f, aspect = 3f, inset = 0.9f)
        assertEquals(972f, bw, 1e-3f)
        assertEquals(324f, bh, 1e-3f)
        // Square frame on a landscape canvas → limited by height.
        val (lw, lh) = CropGeometry.frameSize(1920f, 1080f, aspect = 1f, inset = 0.9f)
        assertEquals(972f, lw, 1e-3f)
        assertEquals(972f, lh, 1e-3f)
    }

    @Test
    fun clampScale_flooredAtCoverFitAndCappedAtMax() {
        assertEquals(1.0f, CropGeometry.clampScale(0.5f, maxScale = 4f)) // can't zoom out past cover
        assertEquals(2.5f, CropGeometry.clampScale(2.5f, maxScale = 4f))
        assertEquals(4.0f, CropGeometry.clampScale(9f, maxScale = 4f)) // capped
    }

    @Test
    fun maxOffset_isHalfTheOverflow() {
        // 2:1 image, 1:1 frame, scale 1: dispW=2000, dispH=1000.
        // maxX = (2000-1000)/2 = 500; maxY = (1000-1000)/2 = 0.
        assertEquals(500f, CropGeometry.maxOffsetX(2000, 1000, 1000f, 1000f, 1f))
        assertEquals(0f, CropGeometry.maxOffsetY(2000, 1000, 1000f, 1000f, 1f))
    }

    @Test
    fun clampOffset_constrainsImageToCoverFrame() {
        val (x, y) = CropGeometry.clampOffset(9999f, -9999f, 2000, 1000, 1000f, 1000f, 1f)
        assertEquals(500f, x) // clamped to +maxX
        assertEquals(0f, y, 1e-6f) // no vertical slack at cover fit (±0f)
    }

    @Test
    fun sourceRect_centeredScale1_takesCenterSquareOfLandscape() {
        // 2:1 image, 1:1 frame, centered, no zoom → center square: x∈[0.25,0.75].
        val r = CropGeometry.sourceRect(2000, 1000, 1000f, 1000f, scale = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(CropRect(500, 0, 1500, 1000), r)
    }

    @Test
    fun sourceRect_pannedToBounds_revealsEdgeSquares() {
        // Pan to +maxX → the left square of the image; to −maxX → the right square.
        assertEquals(
            CropRect(0, 0, 1000, 1000),
            CropGeometry.sourceRect(2000, 1000, 1000f, 1000f, 1f, offsetX = 500f, offsetY = 0f),
        )
        assertEquals(
            CropRect(1000, 0, 2000, 1000),
            CropGeometry.sourceRect(2000, 1000, 1000f, 1000f, 1f, offsetX = -500f, offsetY = 0f),
        )
    }

    @Test
    fun sourceRect_zoomedIn_shrinksTheExtractedRegion() {
        // scale=2 centered on the 2:1 image → x∈[0.375,0.625], y∈[0.25,0.75].
        val r = CropGeometry.sourceRect(2000, 1000, 1000f, 1000f, scale = 2f, offsetX = 0f, offsetY = 0f)
        assertEquals(CropRect(750, 250, 1250, 750), r)
    }

    @Test
    fun sourceRect_banner_takesWideCenterStripe() {
        // 2:1 image, 3:1 banner frame, centered, no zoom.
        // baseScale 0.3 → dispW=1200,dispH=600; y∈[1/6,5/6]→[333,1667], full width.
        val r = CropGeometry.sourceRect(4000, 2000, 1200f, 400f, scale = 1f, offsetX = 0f, offsetY = 0f)
        assertEquals(0, r.left)
        assertEquals(4000, r.right)
        assertEquals(333, r.top)
        assertEquals(1667, r.bottom)
        // ~3:1 output.
        assertTrue(kotlin.math.abs(r.width.toFloat() / r.height - 3f) < 0.02f)
    }

    @Test
    fun sourceRect_isDrivenByOrientedDimensions() {
        // The SAME gesture state against a landscape vs a portrait working
        // bitmap yields different rects — i.e. once ImageDecoder applies the
        // EXIF orientation, this math extracts from the oriented pixels.
        val landscape = CropGeometry.sourceRect(2000, 1000, 1000f, 1000f, 1f, 0f, 0f)
        val portrait = CropGeometry.sourceRect(1000, 2000, 1000f, 1000f, 1f, 0f, 0f)
        assertEquals(CropRect(500, 0, 1500, 1000), landscape)
        assertEquals(CropRect(0, 500, 1000, 1500), portrait) // center square of a portrait
    }

    @Test
    fun sourceRect_alwaysWithinBoundsAndAtLeastOnePx_evenWithGarbageInput() {
        // Out-of-range offset + huge zoom must still yield an in-bounds,
        // non-empty rect (the UI clamps, but the extractor defends too).
        val r = CropGeometry.sourceRect(1080, 1920, 800f, 800f, scale = 50f, offsetX = 1e6f, offsetY = -1e6f)
        assertTrue(r.left in 0..1080 && r.right in 0..1080) { "x out of bounds: $r" }
        assertTrue(r.top in 0..1920 && r.bottom in 0..1920) { "y out of bounds: $r" }
        assertTrue(r.width >= 1 && r.height >= 1) { "rect collapsed: $r" }
    }
}
