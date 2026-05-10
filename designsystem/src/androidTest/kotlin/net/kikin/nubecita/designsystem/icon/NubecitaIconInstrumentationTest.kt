package net.kikin.nubecita.designsystem.icon

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI coverage for [NubecitaIcon].
 *
 * Two contracts pinned:
 * 1. The composable exposes its `contentDescription` on the merged
 *    a11y tree exactly once — TalkBack reads the icon as a single
 *    semantic node.
 * 2. `filled = true` and `filled = false` produce visually distinct
 *    rasters — proving the FILL axis is wired into the variable font.
 */
class NubecitaIconInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersWithContentDescription_andIsExposedToA11y() {
        composeTestRule.setContent {
            NubecitaIcon(
                name = NubecitaIconName.Search,
                contentDescription = "search",
            )
        }
        composeTestRule
            .onNodeWithContentDescription("search")
            .assertIsDisplayed()
    }

    @Test
    fun fillTrue_rasterDiffersFromFillFalse() {
        // Render both fill states side by side, capture each, and
        // assert their bitmaps are not identical. Variable-font FILL
        // axis is a continuous shape change — the outlined and filled
        // glyphs differ in every Material Symbols icon, so any pair
        // suffices to verify the axis is being applied.
        composeTestRule.setContent {
            Row {
                NubecitaIcon(
                    name = NubecitaIconName.Favorite,
                    contentDescription = "outlined",
                    filled = false,
                    modifier = Modifier.testTag("outlined"),
                )
                NubecitaIcon(
                    name = NubecitaIconName.Favorite,
                    contentDescription = "filled",
                    filled = true,
                    modifier = Modifier.testTag("filled"),
                )
            }
        }
        val outlined = composeTestRule.onNodeWithTag("outlined").captureToImage()
        val filled = composeTestRule.onNodeWithTag("filled").captureToImage()
        // captureToImage returns ImageBitmap; convert to pixel arrays
        // for a structural comparison. Any non-zero pixel diff proves
        // the axis is active.
        val outlinedPixels = IntArray(outlined.width * outlined.height)
        val filledPixels = IntArray(filled.width * filled.height)
        outlined.readPixels(outlinedPixels)
        filled.readPixels(filledPixels)
        assertNotEquals(
            "FILL axis is not affecting the rendered glyph — outlined and filled produced identical pixels",
            outlinedPixels.toList(),
            filledPixels.toList(),
        )
    }
}
