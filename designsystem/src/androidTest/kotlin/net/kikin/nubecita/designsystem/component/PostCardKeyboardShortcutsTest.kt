package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.input.KeyboardFocusAnchors
import net.kikin.nubecita.designsystem.input.LocalKeyboardFocusAnchors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

/**
 * Hardware keyboard shortcuts on a focused [PostCard] (nubecita-h6kf.7): `l` likes, `r`
 * replies (unless threadgated), `j` / `k` move focus to the next / previous card. Each guard
 * is checked in both directions (no hardware keyboard means no shortcuts). The text-field
 * guard is in `KeyboardShortcutsTest`.
 */
@OptIn(ExperimentalTestApi::class)
class PostCardKeyboardShortcutsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // A hardware key press takes a real device out of touch mode, and only then can a
    // clickable (the card) hold focus. The test activity starts in touch mode.
    @Before
    fun leaveTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
    }

    // setInTouchMode is device-wide: put it back so later tests (and the device) see touch mode.
    @After
    fun restoreTouchMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(true)
    }

    private val likes = mutableListOf<String>()
    private val replies = mutableListOf<String>()
    private val callbacks =
        PostCallbacks(
            onLike = { likes += it.id },
            onReply = { replies += it.id },
        )

    @Test
    fun l_likesTheFocusedPost() {
        setCards()
        card(FIRST).requestFocus()
        card(FIRST).performKeyInput { pressKey(Key.L) }
        assertEquals(listOf(FIRST.id), likes)
    }

    @Test
    fun r_repliesToTheFocusedPost() {
        setCards()
        card(SECOND).requestFocus()
        card(SECOND).performKeyInput { pressKey(Key.R) }
        assertEquals(listOf(SECOND.id), replies)
    }

    @Test
    fun r_doesNothing_whenTheViewerCannotReply() {
        val gated = SECOND.copy(viewer = ViewerStateUi(canViewerReply = false))
        setCards(second = gated)
        card(gated).requestFocus()
        card(gated).performKeyInput { pressKey(Key.R) }
        assertEquals(emptyList<String>(), replies)
    }

    @Test
    fun j_movesFocusToTheNextPost_andKBack() {
        setCards()
        card(FIRST).requestFocus()
        card(FIRST).performKeyInput { pressKey(Key.J) }
        card(SECOND).assert(isFocused())
        card(SECOND).performKeyInput { pressKey(Key.K) }
        card(FIRST).assert(isFocused())
    }

    @Test
    fun withoutAHardwareKeyboard_lettersDoNothing() {
        setCards(hardwareKeyboard = false)
        card(FIRST).requestFocus()
        card(FIRST).performKeyInput { pressKey(Key.L) }
        assertEquals(emptyList<String>(), likes)
    }

    private fun setCards(
        second: PostUi = SECOND,
        hardwareKeyboard: Boolean = true,
    ) {
        composeTestRule.setContent {
            NubecitaTheme {
                WithKeyboard(hardwareKeyboard) {
                    Column {
                        PostCard(post = FIRST, callbacks = callbacks)
                        PostCard(post = second, callbacks = callbacks)
                    }
                }
            }
        }
    }

    // MainShell provides the anchors only when a hardware keyboard is attached.
    @Composable
    private fun WithKeyboard(
        enabled: Boolean,
        content: @Composable () -> Unit,
    ) {
        val anchors = remember { KeyboardFocusAnchors() }
        CompositionLocalProvider(LocalKeyboardFocusAnchors provides if (enabled) anchors else null, content = content)
    }

    /** The card's own clickable: the node that holds focus and merges the post's text. */
    private fun card(post: PostUi): SemanticsNodeInteraction = composeTestRule.onNode(hasClickAction() and hasText(post.text, substring = true) and isCard)

    private companion object {
        // The card's clickable merges the author line, which is itself clickable; the card is
        // the one that also carries the body text, matched above. Excluding nodes with a role
        // keeps action pills (Role.Button / Role.Switch) out.
        val isCard = SemanticsMatcher.keyNotDefined(SemanticsProperties.Role)

        fun post(
            id: String,
            text: String,
        ) = PostUi(
            id = id,
            cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
            author =
                AuthorUi(
                    did = "did:plc:fakedid000000000000000",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2025-10-15T12:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(replyCount = 1, repostCount = 2, likeCount = 3),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

        val FIRST = post("first", "First post body")
        val SECOND = post("second", "Second post body")
    }
}
