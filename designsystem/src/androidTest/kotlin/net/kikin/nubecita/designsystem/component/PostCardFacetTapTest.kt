package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Runtime tap contract for tappable rich-text facets in a `PostCard` body.
 *
 * The `:designsystem` unit test ([TappableBlueskyAnnotatedStringTest]) proves the
 * built `AnnotatedString` carries the right target per span by invoking each link
 * listener directly. This instrumented test closes the gap that a unit test can't:
 * it dispatches a *real touch* at the glyph, so it verifies that Compose actually
 * wires the `LinkAnnotation` to the gesture pipeline AND that the nested-clickable
 * priority resolves correctly — a tap on a facet span reaches `onFacetTap` and does
 * NOT bubble to the card's `onTap`, a tap on plain body text routes to no facet, and
 * a tap on the author identity row reaches `onAuthorTap` (not the card).
 */
class PostCardFacetTapTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tappingMention_firesOnFacetTapWithMentionDid_notCardTap() {
        var facetTarget: FacetTarget? = null
        var cardTaps = 0
        setPostCard(onTap = { cardTaps++ }, onFacetTap = { facetTarget = it })

        composeTestRule.onNodeWithText(BODY_TEXT, useUnmergedTree = true).clickAtChar(MENTION_CHAR)

        assertEquals(FacetTarget.Mention(ALICE_DID), facetTarget)
        assertEquals("card onTap must not fire when a mention is tapped", 0, cardTaps)
    }

    @Test
    fun tappingLink_firesOnFacetTapWithLinkUri_notCardTap() {
        var facetTarget: FacetTarget? = null
        var cardTaps = 0
        setPostCard(onTap = { cardTaps++ }, onFacetTap = { facetTarget = it })

        composeTestRule.onNodeWithText(BODY_TEXT, useUnmergedTree = true).clickAtChar(LINK_CHAR)

        assertEquals(FacetTarget.Link(LINK_URI), facetTarget)
        assertEquals("card onTap must not fire when a link is tapped", 0, cardTaps)
    }

    @Test
    fun tappingPlainBodyText_doesNotFireAnyFacet() {
        var facetTarget: FacetTarget? = null
        setPostCard(onFacetTap = { facetTarget = it })

        // "Hello" at the start — plain text, no facet span. Only facet spans route;
        // a tap on the surrounding words resolves to no target. (Compose's
        // text-with-links gesture also swallows this tap rather than bubbling to the
        // card onTap — inherent to any post that carries a facet, links included —
        // so this asserts only the facet-negative, which is this feature's contract.)
        composeTestRule.onNodeWithText(BODY_TEXT, useUnmergedTree = true).clickAtChar(PLAIN_CHAR)

        assertNull("plain body text is not a facet, so no target routes", facetTarget)
    }

    @Test
    fun tappingAuthorRow_firesOnAuthorTap_notCardTap() {
        var author: AuthorUi? = null
        var cardTaps = 0
        setPostCard(onTap = { cardTaps++ }, onAuthorTap = { author = it })

        composeTestRule.onNodeWithText(AUTHOR_NAME).performClick()

        assertEquals(AUTHOR, author)
        assertEquals("card onTap must not fire when the author row is tapped", 0, cardTaps)
    }

    private fun setPostCard(
        onTap: (PostUi) -> Unit = {},
        onAuthorTap: (AuthorUi) -> Unit = {},
        onFacetTap: (FacetTarget) -> Unit = {},
    ) {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = facetPost(),
                    callbacks =
                        PostCallbacks(
                            onTap = onTap,
                            onAuthorTap = onAuthorTap,
                            onFacetTap = onFacetTap,
                        ),
                )
            }
        }
    }

    // Click the character at [charOffset] within this text node by resolving its
    // bounding box from the node's TextLayoutResult and dispatching a touch there.
    // A real pointer event at the glyph is the only way to exercise the actual
    // LinkAnnotation gesture wiring (vs. invoking the listener directly).
    private fun SemanticsNodeInteraction.clickAtChar(charOffset: Int) {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult]
            .action
            ?.invoke(results)
        val box = results.first().getBoundingBox(charOffset)
        performTouchInput { click(box.center) }
    }

    private companion object {
        const val ALICE_DID = "did:plc:alice000000000000000"
        const val LINK_URI = "https://nubecita.app"
        const val AUTHOR_NAME = "Post Author"

        // "Hello @alice.bsky.social and https://nubecita.app"
        //  0     6                 24  29                  49   (ASCII → byte == char)
        const val BODY_TEXT = "Hello @alice.bsky.social and https://nubecita.app"
        const val PLAIN_CHAR = 2 // inside "Hello"
        const val MENTION_CHAR = 10 // inside "@alice.bsky.social"
        const val LINK_CHAR = 35 // inside "https://nubecita.app"

        val AUTHOR =
            AuthorUi(
                did = "did:plc:author",
                handle = "author.bsky.social",
                displayName = AUTHOR_NAME,
                avatarUrl = null,
            )

        fun facetPost(): PostUi =
            PostUi(
                id = "at://did:plc:author/app.bsky.feed.post/p",
                cid = "bafyreifacetcid00000000000000000000000000000000000",
                author = AUTHOR,
                createdAt = Clock.System.now() - 3.minutes,
                text = BODY_TEXT,
                facets =
                    persistentListOf(
                        Facet(
                            features = listOf(FacetMention(did = Did(ALICE_DID))),
                            index = FacetByteSlice(byteStart = 6, byteEnd = 24),
                        ),
                        Facet(
                            features = listOf(FacetLink(uri = Uri(LINK_URI))),
                            index = FacetByteSlice(byteStart = 29, byteEnd = 49),
                        ),
                    ),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
