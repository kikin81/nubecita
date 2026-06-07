package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Interaction contract for `PostCard`'s repost cell (nubecita-8g28.5):
 *
 * - **Unwired / non-quotable** — when `onQuote` is null OR the post has quoting
 *   disabled, the cell keeps the plain tap-to-toggle behavior and offers no menu
 *   (no "Quote post" item exists).
 * - **Wired + quotable** — a single tap opens a Repost/Quote menu; the menu's
 *   items invoke `onRepost` / `onQuote`; a long-press performs the repost toggle
 *   directly; the first item reads "Undo repost" once the viewer has reposted.
 */
class PostCardRepostMenuTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val repostLabel: String
        get() = composeTestRule.activity.getString(R.string.postcard_action_repost)
    private val repostOptionsLabel: String
        get() = composeTestRule.activity.getString(R.string.postcard_action_repost_options)
    private val undoRepostLabel: String
        get() = composeTestRule.activity.getString(R.string.postcard_action_undo_repost)
    private val quoteLabel: String
        get() = composeTestRule.activity.getString(R.string.postcard_action_quote)

    @Test
    fun unwiredQuote_tapTogglesRepostDirectly_andNoMenu() {
        var reposts = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(),
                    // onQuote unwired (default null) → plain toggle.
                    callbacks = PostCallbacks(onRepost = { reposts++ }),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostLabel).performClick()

        assertEquals("tap should toggle repost directly when no menu", 1, reposts)
        composeTestRule.onNodeWithText(quoteLabel).assertDoesNotExist()
    }

    @Test
    fun wiredQuote_tapOpensMenu_withRepostAndQuote() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(),
                    callbacks = PostCallbacks(onRepost = {}, onQuote = {}),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostOptionsLabel).performClick()

        composeTestRule.onNodeWithText(repostLabel).assertIsDisplayed()
        composeTestRule.onNodeWithText(quoteLabel).assertIsDisplayed()
    }

    @Test
    fun wiredQuote_quoteItem_invokesOnQuoteWithPost() {
        var quoted: PostUi? = null
        val target = post()
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = target,
                    callbacks = PostCallbacks(onRepost = {}, onQuote = { quoted = it }),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostOptionsLabel).performClick()
        composeTestRule.onNodeWithText(quoteLabel).performClick()

        assertSame("Quote item should pass the rendered post", target, quoted)
    }

    @Test
    fun wiredQuote_repostItem_invokesOnRepost() {
        var reposts = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(),
                    callbacks = PostCallbacks(onRepost = { reposts++ }, onQuote = {}),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostOptionsLabel).performClick()
        composeTestRule.onNodeWithText(repostLabel).performClick()

        assertEquals(1, reposts)
    }

    @Test
    fun wiredQuote_alreadyReposted_menuShowsUndo() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(isReposted = true),
                    callbacks = PostCallbacks(onRepost = {}, onQuote = {}),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostOptionsLabel).performClick()

        composeTestRule.onNodeWithText(undoRepostLabel).assertIsDisplayed()
    }

    @Test
    fun wiredQuote_longPressTogglesRepostWithoutMenu() {
        var reposts = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = post(),
                    callbacks = PostCallbacks(onRepost = { reposts++ }, onQuote = {}),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostOptionsLabel).performTouchInput { longClick() }

        assertEquals("long-press should toggle repost directly", 1, reposts)
    }

    @Test
    fun wiredQuote_butQuotingDisabled_noMenu_andDirectToggle() {
        var reposts = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    // canViewerQuote = false → read-side postgate hides Quote, so
                    // the cell falls back to the plain toggle even with onQuote wired.
                    post = post(canViewerQuote = false),
                    callbacks = PostCallbacks(onRepost = { reposts++ }, onQuote = {}),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription(repostLabel).performClick()

        assertEquals(1, reposts)
        composeTestRule.onNodeWithText(quoteLabel).assertDoesNotExist()
    }

    private companion object {
        val AUTHOR =
            AuthorUi(
                did = "did:plc:author",
                handle = "author.bsky.social",
                displayName = "Author",
                avatarUrl = null,
            )

        fun post(
            isReposted: Boolean = false,
            canViewerQuote: Boolean = true,
        ): PostUi =
            PostUi(
                id = "at://did:plc:author/app.bsky.feed.post/p",
                cid = "bafyreipostcid00000000000000000000000000000000000",
                author = AUTHOR,
                createdAt = Clock.System.now() - 3.minutes,
                text = "Post body for the repost-menu interaction test.",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(repostCount = 3),
                viewer =
                    ViewerStateUi(
                        isRepostedByViewer = isReposted,
                        canViewerQuote = canViewerQuote,
                    ),
                repostedBy = null,
            )
    }
}
