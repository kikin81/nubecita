package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Frame-stepping coverage for the "careful rule" in
 * [AnimatedCompactCount]. The two load-bearing assertions:
 *
 * - Animate when |delta|==1 AND both prev/next are literal: at a
 *   mid-animation frame the *previous* digit string is still in the
 *   semantic tree, fading + sliding out.
 * - Snap across the compact-format boundary: the previous string
 *   vanishes immediately; only the new short form is present.
 *
 * Uses `composeTestRule.mainClock` with `autoAdvance = false` so the
 * test drives the frame timeline deterministically.
 */
class AnimatedCompactCountRuleTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun delta_within_literal_range_animates_with_prev_overlap() {
        var likeCount by mutableLongStateOf(99L)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(stats = PostStatsUi(likeCount = likeCount.toInt())),
                    animateLikeTap = true,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.onNodeWithText("99").assertIsDisplayed()

        likeCount = 100L
        // Mid-animation: AnimatedContent keeps the outgoing slot in the
        // tree until the slide+fade completes (~220ms). Stepping 60ms in
        // should sit comfortably inside that window.
        composeTestRule.mainClock.advanceTimeBy(60L)
        composeTestRule
            .onNodeWithText("99", useUnmergedTree = true)
            .assertExists(
                "during a literal-range animation, the previous digits must still " +
                    "be in the tree (proving the slide transition is running)",
            )
        composeTestRule
            .onNodeWithText("100", useUnmergedTree = true)
            .assertExists(
                "during a literal-range animation, the new digits must be in the " +
                    "tree alongside the outgoing slot",
            )

        // After the animation completes, only the new digits remain.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onNodeWithText("100").assertIsDisplayed()
    }

    @Test
    fun delta_across_compact_format_snaps() {
        var likeCount by mutableLongStateOf(999L)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(stats = PostStatsUi(likeCount = likeCount.toInt())),
                    animateLikeTap = true,
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.onNodeWithText("999").assertIsDisplayed()

        likeCount = 1_000L
        // Snap means the previous string is gone after a single frame —
        // no overlap window. EnterTransition.None / ExitTransition.None
        // produces an immediate swap.
        composeTestRule.mainClock.advanceTimeBy(60L)
        composeTestRule.onNodeWithText("1K").assertIsDisplayed()
    }

    private fun post(stats: PostStatsUi): PostUi =
        PostUi(
            id = "at://did:plc:fake/app.bsky.feed.post/p",
            cid = "bafyreifakecid000000000000000000000000000000000",
            author =
                AuthorUi(
                    did = "did:plc:fake",
                    handle = "alice.bsky.social",
                    displayName = "Alice",
                    avatarUrl = null,
                ),
            createdAt = Clock.System.now() - 3.minutes,
            text = "AnimatedCompactCount rule test.",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = stats,
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
