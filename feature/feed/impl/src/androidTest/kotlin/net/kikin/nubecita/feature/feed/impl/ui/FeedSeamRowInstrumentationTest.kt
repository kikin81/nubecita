package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.test.junit4.createComposeRule
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The seam's self-fill, at the level the bug lived: whether a row that is
 * composed asks for a catch-up, or only one that is actually put on screen.
 *
 * A `LazyColumn` prefetch precomposes and premeasures a row without placing
 * it, and effects do run in that precomposition — so a fill keyed on
 * composition fires for rows the reader never reaches, and fires again every
 * time a scroll reversal discards the composition and a later pass rebuilds
 * it. [composedButNotPlaced] reproduces that shape exactly, and
 * deterministically, which a real prefetch under a test rule does not.
 */
class FeedSeamRowInstrumentationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aRowComposedButNeverPlaced_asksForNoFill() {
        var fills = 0
        composeTestRule.setContent {
            NubecitaTheme {
                ComposedButNotPlaced {
                    FeedSeamRow(exhausted = false, isFilling = false, onFill = { fills++ }, onJumpToNewest = {})
                }
            }
        }

        composeTestRule.waitForIdle()

        assertEquals(0, fills)
    }

    @Test
    fun aRowOnScreen_fillsExactlyOnce() {
        var fills = 0
        composeTestRule.setContent {
            NubecitaTheme {
                FeedSeamRow(exhausted = false, isFilling = false, onFill = { fills++ }, onJumpToNewest = {})
            }
        }

        composeTestRule.waitForIdle()

        assertEquals(1, fills)
    }

    @Test
    fun anExhaustedRowOnScreen_fillsNothing() {
        // Nothing to fill: the gap is wider than the feed keeps, so the row
        // offers jump-to-newest instead and must not reach for the network.
        var fills = 0
        composeTestRule.setContent {
            NubecitaTheme {
                FeedSeamRow(exhausted = true, isFilling = false, onFill = { fills++ }, onJumpToNewest = {})
            }
        }

        composeTestRule.waitForIdle()

        assertEquals(0, fills)
    }
}

/**
 * Composes and measures [content], then places none of it — what a lazy list
 * does to a row it prefetches and never shows.
 */
@Composable
private fun ComposedButNotPlaced(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        measurables.forEach { it.measure(constraints) }
        layout(0, 0) {}
    }
}
