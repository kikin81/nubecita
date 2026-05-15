package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
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
 * Smoke coverage for compact-count rendering in `PostCard`'s action
 * row. The underlying [net.kikin.nubecita.core.common.text.formatCompactCount]
 * helper sits on `android.icu.text.CompactDecimalFormat` which isn't
 * available to plain JVM unit tests — so we exercise the integration
 * here, on a real device, where the ICU tables ship with the OS.
 *
 * Anchors are written against en-US (the test runner's default
 * locale on AVD images). Spanish / Hindi behaviour is validated by
 * the screenshot suite where locale variants land.
 */
class PostCardCompactCountTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun likeCount_under_one_thousand_renders_literally() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(post = post(stats = PostStatsUi(likeCount = 86)))
            }
        }
        composeTestRule.onNodeWithText("86").assertIsDisplayed()
    }

    @Test
    fun likeCount_above_one_thousand_renders_abbreviated() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(post = post(stats = PostStatsUi(likeCount = 1_234)))
            }
        }
        // ICU's SHORT style: 1234 → "1.2K".
        composeTestRule.onNodeWithText("1.2K").assertIsDisplayed()
    }

    @Test
    fun all_three_counts_abbreviate_independently() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post =
                        post(
                            stats =
                                PostStatsUi(
                                    replyCount = 12,
                                    repostCount = 5_500,
                                    likeCount = 1_200_000,
                                ),
                        ),
                )
            }
        }
        composeTestRule.onNodeWithText("12").assertIsDisplayed()
        composeTestRule.onNodeWithText("5.5K").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.2M").assertIsDisplayed()
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
            text = "Post body — count rendering test.",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = stats,
            viewer = ViewerStateUi(),
            repostedBy = null,
        )
}
