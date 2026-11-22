package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadPositionUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * What a screen reader is told about the thread pill.
 *
 * On its own the pill announces "Post 3 of 5". Inside a post body it must stay
 * silent, because the body's own text already ends with those words (the
 * inline slot's alternate text): a reader should hear the position once, as the
 * end of the post, not a second time as a stray "3/5" or a repeated phrase.
 */
class ThreadPositionSemanticsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val position = ThreadPositionUi(index = 3, count = 5)

    @Test
    fun thePillOnItsOwnAnnouncesThePosition() {
        composeTestRule.setContent { NubecitaTheme(dynamicColor = false) { ThreadPositionBadge(position = position) } }

        composeTestRule.onNodeWithContentDescription("Post 3 of 5").assertExists()
    }

    @Test
    fun insideAPostThePositionIsAnnouncedOnceByTheBodyText() {
        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                PostCard(
                    post =
                        PostUi(
                            id = "at://did:plc:a11y/app.bsky.feed.post/thread",
                            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
                            author = AuthorUi(did = "did:plc:a11y", handle = "a11y.test", displayName = "A11y Test", avatarUrl = null),
                            createdAt = Instant.parse("2025-10-15T12:00:00Z"),
                            text = "Third post of the thread.",
                            facets = persistentListOf(),
                            embed = EmbedUi.Empty,
                            stats = PostStatsUi(),
                            viewer = ViewerStateUi(),
                            repostedBy = null,
                            threadPosition = position,
                        ),
                )
            }
        }

        // The body says it, as the end of the post text.
        composeTestRule
            .onAllNodes(hasText("Third post of the thread. Post 3 of 5"), useUnmergedTree = true)
            .assertCountEquals(1)
        // The pill drawn into the text says nothing of its own: neither its
        // description nor its visible "3/5".
        composeTestRule.onAllNodes(hasContentDescription("Post 3 of 5"), useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodes(hasText("3/5"), useUnmergedTree = true).assertCountEquals(0)
    }
}
