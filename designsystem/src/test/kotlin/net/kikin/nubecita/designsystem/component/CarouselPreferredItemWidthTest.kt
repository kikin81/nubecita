package net.kikin.nubecita.designsystem.component

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * How many images the multi-image carousel shows, and at what width, for the
 * canvases the app runs on.
 *
 * The expected widths are what make M3's `HorizontalMultiBrowseCarousel` take
 * the arrangement without squeezing: verified against a transcription of
 * material3 1.5.0-rc01's `multiBrowseKeylineList` (nubecita-3w2k), where the
 * old fixed 220dp gave a ~319dp phone two "large" items of ~102dp.
 */
internal class CarouselPreferredItemWidthTest {
    @Test
    fun `a phone card shows one large image and a peek`() {
        // 319 - 56 peek - 4 gap = 259
        assertDp(259f, carouselPreferredItemWidth(319.dp))
        assertDp(352f, carouselPreferredItemWidth(412.dp))
    }

    @Test
    fun `a foldable's inner screen shows two`() {
        // (560 - 56 - 4 - 4) / 2 = 248
        assertDp(248f, carouselPreferredItemWidth(560.dp))
    }

    @Test
    fun `a tablet column shows three`() {
        // (840 - 56 - 4 - 8) / 3 = 257.33
        assertDp(257.33f, carouselPreferredItemWidth(840.dp))
    }

    @Test
    fun `no large image is narrower than the target once one fits beside the peek`() {
        // 280dp = 220 target + 56 peek + 4 gap: from there on, the count only
        // goes up when another full-size image fits, so none is squeezed.
        (280..1400).forEach { width ->
            val preferred = carouselPreferredItemWidth(width.dp)
            assertTrue(preferred >= 220.dp, "a ${width}dp card got ${preferred.value}dp large images")
        }
    }

    @Test
    fun `a card narrower than the target still gets one image`() {
        // Never zero or negative: one image takes what's left beside the peek.
        assertDp(140f, carouselPreferredItemWidth(200.dp))
    }

    @Test
    fun `a width too narrow for a peek never yields a zero or negative width`() {
        // M3 divides the carousel width by this; zero or negative throws.
        listOf(0.dp, 20.dp, 59.dp, 60.dp).forEach { width ->
            assertTrue(carouselPreferredItemWidth(width) >= 1.dp, "${width.value}dp gave a non-positive width")
        }
    }

    @Test
    fun `an unbounded or unspecified width falls back to the target`() {
        assertDp(220f, carouselPreferredItemWidth(Dp.Infinity))
        assertDp(220f, carouselPreferredItemWidth(Dp.Unspecified))
    }

    private fun assertDp(
        expected: Float,
        actual: Dp,
    ) = assertEquals(expected, actual.value, 0.01f)
}
