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
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
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
        }
        val bare = composeTestRule.onNodeWithTag("bare").captureToImage()
        val mirrored = composeTestRule.onNodeWithTag("mirrored").captureToImage()
        val barePixels = IntArray(bare.width * bare.height).also(bare::readPixels)
        val mirroredPixels = IntArray(mirrored.width * mirrored.height).also(mirrored::readPixels)
        assertFalse(
            "In RTL, Modifier.mirror() must produce a horizontally-flipped raster",
            barePixels.contentEquals(mirroredPixels),
        )
    }
}
