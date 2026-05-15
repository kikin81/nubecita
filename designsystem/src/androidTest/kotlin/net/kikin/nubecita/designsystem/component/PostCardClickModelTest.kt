package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.QuotedEmbedUi
import net.kikin.nubecita.data.models.QuotedPostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Click-model contract for `PostCard` when the embed slot resolves to
 * a quoted post:
 *
 * - Tapping the inner quoted-card region fires `onQuotedPostTap` exactly
 *   once and does NOT fire the outer `onTap`. Compose's nested clickable
 *   semantics consume the gesture so the parent never receives it.
 * - Tapping the outer body / author region fires `onTap` and does NOT
 *   fire `onQuotedPostTap`.
 *
 * Repeated for `EmbedUi.RecordWithMedia`: the inner quoted region's
 * negative-on-parent invariant holds, AND tapping the media region
 * (above the quoted card) does NOT fire `onQuotedPostTap` — that
 * gesture belongs to the media leaf's own click target.
 */
class PostCardClickModelTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun record_tappingQuotedRegion_firesQuotedTap_not_parentTap() {
        var parentTaps = 0
        var quotedTaps = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Record(quotedPost = QUOTED_POST)),
                    callbacks =
                        PostCallbacks(
                            onTap = { parentTaps++ },
                            onQuotedPostTap = { quotedTaps++ },
                        ),
                )
            }
        }

        // QuotedBodyText is unique to the quoted card; clicking it
        // hits the Surface.clickable on PostCardQuotedPost.
        composeTestRule.onNodeWithText(QUOTED_BODY_TEXT).performClick()

        assertEquals("quoted tap should fire exactly once", 1, quotedTaps)
        assertEquals("parent tap must not fire when quoted region is clicked", 0, parentTaps)
    }

    @Test
    fun record_tappingOuterBody_firesParentTap_not_quotedTap() {
        var parentTaps = 0
        var quotedTaps = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Record(quotedPost = QUOTED_POST)),
                    callbacks =
                        PostCallbacks(
                            onTap = { parentTaps++ },
                            onQuotedPostTap = { quotedTaps++ },
                        ),
                )
            }
        }

        // useUnmergedTree = true so performClick targets the Text's own
        // bounds (above the embed), not the merged inner-Column's geometric
        // center (which lands inside the quoted Surface and triggers the
        // wrong clickable). The click still propagates through the gesture
        // pipeline to the outer clickable Column.
        composeTestRule
            .onNodeWithText(PARENT_BODY_TEXT, useUnmergedTree = true)
            .performClick()

        assertEquals("parent tap should fire exactly once", 1, parentTaps)
        assertEquals("quoted tap must not fire when outer body is clicked", 0, quotedTaps)
    }

    @Test
    fun recordWithMedia_tappingQuotedRegion_firesQuotedTap_not_parentTap() {
        var parentTaps = 0
        var quotedTaps = 0
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post =
                        parentPost(
                            embed =
                                EmbedUi.RecordWithMedia(
                                    record = EmbedUi.Record(quotedPost = QUOTED_POST),
                                    media = MEDIA_IMAGES,
                                ),
                        ),
                    callbacks =
                        PostCallbacks(
                            onTap = { parentTaps++ },
                            onQuotedPostTap = { quotedTaps++ },
                        ),
                )
            }
        }

        composeTestRule.onNodeWithText(QUOTED_BODY_TEXT).performClick()

        assertEquals(1, quotedTaps)
        assertEquals(0, parentTaps)
    }

    @Test
    fun recordWithMedia_tappingNonQuotedRegion_doesNotFireQuotedTap() {
        var quotedTaps = 0
        // Negative-only assertion: we don't pin parent-tap behavior here —
        // image taps inside RecordWithMedia route through the media leaf's
        // own click target (or fall through to parent), which is out of
        // scope. The contract under test is that the quoted-tap callback
        // is scoped to the quoted Surface and nothing else.
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post =
                        parentPost(
                            embed =
                                EmbedUi.RecordWithMedia(
                                    record = EmbedUi.Record(quotedPost = QUOTED_POST),
                                    media = MEDIA_IMAGES,
                                ),
                        ),
                    callbacks =
                        PostCallbacks(
                            onTap = {},
                            onQuotedPostTap = { quotedTaps++ },
                        ),
                )
            }
        }

        // Clicking the parent post's body text proves that we're hitting
        // a region OUTSIDE the quoted card. useUnmergedTree so the click
        // lands on the Text's own bounds (above the embed), not the merged
        // parent's geometric center.
        composeTestRule
            .onNodeWithText(PARENT_BODY_TEXT, useUnmergedTree = true)
            .performClick()

        assertEquals("quoted tap must not fire from non-quoted regions", 0, quotedTaps)
    }

    private companion object {
        const val PARENT_BODY_TEXT =
            "Parent post body text — outside the quoted region."
        const val QUOTED_BODY_TEXT =
            "Quoted post body text — inside the quoted Surface."

        val PARENT_AUTHOR: AuthorUi =
            AuthorUi(
                did = "did:plc:parent",
                handle = "parent.bsky.social",
                displayName = "Parent Author",
                avatarUrl = null,
            )

        val QUOTED_AUTHOR: AuthorUi =
            AuthorUi(
                did = "did:plc:quoted",
                handle = "quoted.bsky.social",
                displayName = "Quoted Author",
                avatarUrl = null,
            )

        val QUOTED_POST: QuotedPostUi =
            QuotedPostUi(
                uri = "at://did:plc:quoted/app.bsky.feed.post/q",
                cid = "bafyreifakequotedcid000000000000000000000000000",
                author = QUOTED_AUTHOR,
                createdAt = Clock.System.now() - 60.minutes,
                text = QUOTED_BODY_TEXT,
                facets = persistentListOf(),
                embed = QuotedEmbedUi.Empty,
            )

        val MEDIA_IMAGES: EmbedUi.Images =
            EmbedUi.Images(
                items =
                    persistentListOf(
                        ImageUi(
                            fullsizeUrl = "https://example.com/preview.jpg",
                            thumbUrl = "https://example.com/preview.jpg",
                            altText = null,
                            aspectRatio = 16f / 9f,
                        ),
                    ),
            )

        fun parentPost(embed: EmbedUi): PostUi =
            PostUi(
                id = "at://did:plc:parent/app.bsky.feed.post/p",
                cid = "bafyreiparentcid000000000000000000000000000000000",
                author = PARENT_AUTHOR,
                createdAt = Clock.System.now() - 3.minutes,
                text = PARENT_BODY_TEXT,
                facets = persistentListOf(),
                embed = embed,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
            )
    }
}
