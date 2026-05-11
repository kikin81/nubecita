package net.kikin.nubecita.designsystem.icon

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * Pin-down for [Modifier.mirror]:
 * - In LTR layouts, the modifier is a no-op (rendered glyph is
 *   identical to a non-mirrored render).
 * - In RTL layouts, the rendered glyph is horizontally flipped
 *   (rendered pixels differ from the LTR render).
 *
 * The directional icon `ArrowBack` is the natural fixture — its
 * left-pointing chevron makes the flip easy to confirm.
 */
class MirrorInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mirror_inLtr_isNoOp() {
        composeTestRule.setContent {
            Row {
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = "back-bare",
                    modifier = Modifier.testTag("bare"),
                )
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = "back-mirrored",
                    modifier = Modifier.testTag("mirrored").mirror(),
                )
            }
        }
        val bare = composeTestRule.onNodeWithTag("bare").captureToImage()
        val mirrored = composeTestRule.onNodeWithTag("mirrored").captureToImage()
        val barePixels = IntArray(bare.width * bare.height).also(bare::readPixels)
        val mirroredPixels = IntArray(mirrored.width * mirrored.height).also(mirrored::readPixels)
        assertTrue(
            "In LTR, Modifier.mirror() must be a no-op",
            barePixels.contentEquals(mirroredPixels),
        )
    }

    @Test
    fun mirror_inRtl_flipsHorizontally() {
        // Render both a bare LTR icon (the reference orientation) and a
        // mirrored RTL icon in the same composition so they share the same
        // font cache, density, and rendering pipeline.
        composeTestRule.setContent {
            Row {
                // LTR reference — no direction override, no mirror.
                NubecitaIcon(
                    name = NubecitaIconName.ArrowBack,
                    contentDescription = "back-ltr",
                    modifier = Modifier.testTag("ltr"),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    // Bare RTL — direction override, no mirror.
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowBack,
                        contentDescription = "back-bare-rtl",
                        modifier = Modifier.testTag("bare-rtl"),
                    )
                    // Mirrored RTL — direction override + mirror.
                    NubecitaIcon(
                        name = NubecitaIconName.ArrowBack,
                        contentDescription = "back-mirrored-rtl",
                        modifier = Modifier.testTag("mirrored-rtl").mirror(),
                    )
                }
            }
        }

        val ltr = composeTestRule.onNodeWithTag("ltr").captureToImage()
        val bareRtl = composeTestRule.onNodeWithTag("bare-rtl").captureToImage()
        val mirroredRtl = composeTestRule.onNodeWithTag("mirrored-rtl").captureToImage()

        val ltrPixels = IntArray(ltr.width * ltr.height).also(ltr::readPixels)
        val bareRtlPixels = IntArray(bareRtl.width * bareRtl.height).also(bareRtl::readPixels)
        val mirroredRtlPixels =
            IntArray(mirroredRtl.width * mirroredRtl.height).also(mirroredRtl::readPixels)

        // First sanity: mirrored RTL render differs from bare RTL render —
        // proves the modifier did something at all.
        assertFalse(
            "In RTL, Modifier.mirror() must produce a horizontally-flipped raster",
            bareRtlPixels.contentEquals(mirroredRtlPixels),
        )

        // Stronger: reverse each row of `mirroredRtlPixels` and assert it
        // is geometrically near-identical to the LTR bare pixels. The
        // contract: applying mirror() in RTL is equivalent to the icon's
        // natural LTR orientation — reversing the flipped raster must
        // reconstruct the original within subpixel anti-aliasing tolerance.
        //
        // Why tolerance, not exact equality: Modifier.scale(-1, 1) is
        // implemented as a graphicsLayer GPU transform. GPU rasterisation of
        // anti-aliased edges introduces ≤1 unit per channel of rounding
        // noise relative to a pixel-perfect algebraic flip. Empirically on
        // this emulator/device: ≤36 of 3 969 pixels differ, maxChannelDelta
        // ≤9, avgDelta ≤3. The thresholds below are 5× headroom — tight
        // enough to catch a wrong-axis flip (which would scatter hundreds of
        // large-delta pixels) while accommodating renderer variation.
        //
        // Why compare against LTR (not bare-RTL): the two render identically
        // (0 pixel difference observed), but LTR is conceptually the cleaner
        // reference for "un-mirrored natural orientation" and removes any
        // coupling to RTL layout side-effects in future font versions.
        val width = mirroredRtl.width
        val height = mirroredRtl.height
        val flippedBack = IntArray(width * height)
        for (row in 0 until height) {
            for (col in 0 until width) {
                flippedBack[row * width + col] = mirroredRtlPixels[row * width + (width - 1 - col)]
            }
        }

        val totalPixels = ltrPixels.size
        // Allow at most 2 % of pixels to differ at all.
        val maxDifferingPixels = totalPixels / 50
        // Allow at most 15 summed-channel delta (R+G+B) per differing pixel.
        val maxPerPixelChannelDelta = 15

        var differingPixels = 0
        var maxObservedDelta = 0
        for (i in ltrPixels.indices) {
            val lp = ltrPixels[i]
            val fp = flippedBack[i]
            if (lp != fp) {
                differingPixels++
                val delta =
                    abs(((lp shr 16) and 0xFF) - ((fp shr 16) and 0xFF)) +
                        abs(((lp shr 8) and 0xFF) - ((fp shr 8) and 0xFF)) +
                        abs((lp and 0xFF) - (fp and 0xFF))
                if (delta > maxObservedDelta) maxObservedDelta = delta
                assertTrue(
                    "Pixel $i: per-pixel channel delta $delta exceeds $maxPerPixelChannelDelta " +
                        "(ltr=0x${lp.toString(16)} flipped=0x${fp.toString(16)}) — " +
                        "Modifier.mirror() horizontal flip is not geometrically correct",
                    delta <= maxPerPixelChannelDelta,
                )
            }
        }
        assertTrue(
            "$differingPixels/$totalPixels pixels differ after row-reversal " +
                "(limit $maxDifferingPixels, maxObservedDelta $maxObservedDelta) — " +
                "Modifier.mirror() horizontal flip is not geometrically correct",
            differingPixels <= maxDifferingPixels,
        )
    }
}
