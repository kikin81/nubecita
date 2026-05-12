package net.kikin.nubecita.designsystem.component

import net.kikin.nubecita.data.models.ImageUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pure-function tests for [displayedAspectRatio] — the clamp math used
 * by `SingleImage` to derive the layout aspect from the post's lexicon
 * `aspectRatio` (width/height). No Compose harness; no Android.
 */
class ImageEmbedAspectRatioTest {
    @Test
    fun landscapeWithinRange_rendersNatively() {
        // 16:9 ≈ 1.78 — well inside [2/3, 3/1], no clamp triggered.
        val image = imageWithAspect(16f / 9f)
        assertEquals(16f / 9f, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun portraitWithinRange_rendersNatively() {
        // 4:5 = 0.8 — the IGN / head-and-shoulders case; inside the
        // range, no crop triggered. This is the bug-fix scenario from
        // nubecita-k9k.
        val image = imageWithAspect(4f / 5f)
        assertEquals(4f / 5f, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun squareWithinRange_rendersNatively() {
        val image = imageWithAspect(1f)
        assertEquals(1f, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun ultraTallPhoneScreenshot_clampsToMinPortrait() {
        // 9:16 = 0.5625 — below MIN_ASPECT_RATIO; clamps to 2:3.
        val image = imageWithAspect(9f / 16f)
        assertEquals(MIN_ASPECT_RATIO, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun longWebcomicVertical_clampsToMinPortrait() {
        // 1:5 = 0.2 — extreme vertical; clamps to 2:3 (we never let a
        // single post exceed this aspect in feed).
        val image = imageWithAspect(1f / 5f)
        assertEquals(MIN_ASPECT_RATIO, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun ultraWidePano_clampsToMaxLandscape() {
        // 32:9 ≈ 3.56 — above MAX_ASPECT_RATIO; clamps to 3:1.
        val image = imageWithAspect(32f / 9f)
        assertEquals(MAX_ASPECT_RATIO, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun nullAspectRatio_fallsBackToSquare() {
        // Pre-2024 Bluesky posts didn't carry aspectRatio in the lexicon.
        // Fallback is square (1.0), which is inside the clamp range so
        // no crop triggers.
        val image = imageWithAspect(null)
        assertEquals(FALLBACK_ASPECT_RATIO, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun exactMinBoundary_passesThrough() {
        // Source aspect that is exactly the clamp boundary should pass
        // through unchanged (coerceIn is inclusive on both ends).
        val image = imageWithAspect(MIN_ASPECT_RATIO)
        assertEquals(MIN_ASPECT_RATIO, image.displayedAspectRatio(), 0.0001f)
    }

    @Test
    fun exactMaxBoundary_passesThrough() {
        val image = imageWithAspect(MAX_ASPECT_RATIO)
        assertEquals(MAX_ASPECT_RATIO, image.displayedAspectRatio(), 0.0001f)
    }

    private fun imageWithAspect(aspect: Float?): ImageUi =
        ImageUi(
            fullsizeUrl = "https://example.com/x.jpg",
            thumbUrl = "https://example.com/x.jpg",
            altText = null,
            aspectRatio = aspect,
        )
}
