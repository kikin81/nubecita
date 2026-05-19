package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Click-model contract for [BlockedPostCard]:
 *
 * - When `onUnblock` is non-null, the "Unblock" TextButton renders and
 *   tapping it fires the callback exactly once.
 * - When `onUnblock` is null, the "Unblock" TextButton does NOT render —
 *   the row degrades to text-only ("Post from a blocked user") and there
 *   is no clickable affordance to fire.
 *
 * Pinned in androidTest rather than the screenshotTest source set because
 * the assertion is about gesture wiring + presence, not pixel rendering.
 * Screenshot baselines for the visual shape live in
 * `BlockedPostCardScreenshotTest`.
 */
class BlockedPostCardClickModelTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val unblockLabel: String
        get() = composeTestRule.activity.getString(R.string.tombstone_unblock)

    private val noticeLabel: String
        get() = composeTestRule.activity.getString(R.string.tombstone_blocked_post)

    @Test
    fun withUnblockCallback_tappingUnblock_firesCallbackOnce() {
        var unblockTaps = 0
        composeTestRule.setContent {
            NubecitaTheme {
                BlockedPostCard(onUnblock = { unblockTaps++ })
            }
        }

        composeTestRule.onNodeWithText(noticeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(unblockLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(unblockLabel).performClick()

        assertEquals("Unblock callback should fire exactly once", 1, unblockTaps)
    }

    @Test
    fun withoutUnblockCallback_unblockButtonIsNotRendered() {
        composeTestRule.setContent {
            NubecitaTheme {
                BlockedPostCard(onUnblock = null)
            }
        }

        // Notice text is always present; the Unblock button is the
        // conditional element that must not render when onUnblock is null.
        composeTestRule.onNodeWithText(noticeLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(unblockLabel).assertDoesNotExist()
    }
}
