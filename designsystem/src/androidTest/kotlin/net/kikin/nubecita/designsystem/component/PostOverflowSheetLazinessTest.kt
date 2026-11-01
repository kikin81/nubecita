package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Pins that a closed post overflow costs nothing on the scroll path
 * (nubecita-iwjz.3): until "…" is tapped, a [PostCard] composes no
 * [PostOverflowSheet] — no sheet, no sheet state, no rows. The open case is
 * asserted too, so the absence check can't pass for the wrong reason.
 */
class PostOverflowSheetLazinessTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val moreOptionsLabel = context.getString(R.string.postcard_action_more)
    private val copyTextLabel = context.getString(R.string.moderation_action_copy_post_text)

    @Test
    fun closedOverflowComposesNoSheet() {
        setCard()

        composeTestRule.onNodeWithTag(POST_OVERFLOW_SHEET_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithText(copyTextLabel).assertDoesNotExist()
    }

    @Test
    fun openedOverflowShowsTheGroupedSheet() {
        setCard()

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(POST_OVERFLOW_SHEET_TAG).assertExists()
        composeTestRule.onNodeWithText(copyTextLabel).assertIsDisplayed()
    }

    private fun setCard() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(post = POST, callbacks = PostCallbacks(onOverflowAction = { _, _ -> }))
            }
        }
    }

    private companion object {
        val POST =
            PostUi(
                id = "at://did:plc:a/app.bsky.feed.post/1",
                cid = "bafy",
                author = AuthorUi(did = "did:plc:a", handle = "a.bsky.social", displayName = "A", avatarUrl = null),
                createdAt = Instant.parse("2025-10-15T12:00:00Z"),
                text = "hello",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
