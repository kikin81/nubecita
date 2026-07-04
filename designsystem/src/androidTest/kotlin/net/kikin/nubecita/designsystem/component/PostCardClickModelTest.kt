package net.kikin.nubecita.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
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
import net.kikin.nubecita.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // Labels resolved from the host activity's resources rather than
    // hardcoded literals so the tests track strings.xml automatically —
    // a copy or l10n change only needs to update the resource, not also
    // a parallel constant table. `get()` because composeTestRule.activity
    // isn't initialized at field-init time.
    private val moreOptionsLabel: String
        get() = composeTestRule.activity.getString(R.string.postcard_action_more)
    private val reportPostLabel: String
        get() = composeTestRule.activity.getString(R.string.moderation_action_report_post)
    private val muteThreadLabel: String
        get() = composeTestRule.activity.getString(R.string.moderation_action_mute_thread)
    private val copyPostTextLabel: String
        get() = composeTestRule.activity.getString(R.string.moderation_action_copy_post_text)

    private fun muteAuthorLabel(handle: String = PARENT_AUTHOR.handle): String = composeTestRule.activity.getString(R.string.moderation_action_mute_author, handle)

    private fun unmuteAuthorLabel(handle: String = PARENT_AUTHOR.handle): String = composeTestRule.activity.getString(R.string.moderation_action_unmute_author, handle)

    private fun blockAuthorLabel(handle: String = PARENT_AUTHOR.handle): String = composeTestRule.activity.getString(R.string.moderation_action_block_author, handle)

    private fun unblockAuthorLabel(handle: String = PARENT_AUTHOR.handle): String = composeTestRule.activity.getString(R.string.moderation_action_unblock_author, handle)

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

    // ---------- overflow-menu (oftc.2) ----------

    @Test
    fun overflowIcon_isNotRendered_whenCallbackIsNull() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty),
                    callbacks = PostCallbacks.None,
                )
            }
        }

        // PostCallbacks.None has onOverflowAction = null → no More-options
        // affordance. assertDoesNotExist() rather than fishing for a click
        // — the contract is "no node whose OnClick action carries this label"
        // (PostStat exposes the overflow verb via onClickLabel, not
        // contentDescription).
        composeTestRule
            .onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun overflowIcon_isRendered_whenCallbackProvided() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty),
                    callbacks =
                        PostCallbacks(
                            // Recording lambda kept empty — the test only
                            // asserts the icon's render-gate, not the dispatch.
                            onOverflowAction = { _, _ -> },
                        ),
                )
            }
        }

        composeTestRule
            .onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_reportPost_invokesCallbackWithReportPost() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(reportPostLabel).performClick()

        assertEquals(PostOverflowAction.ReportPost, recorded)
    }

    @Test
    fun overflowMenu_muteAuthor_invokesCallbackWithMuteAuthor_whenNotMuted() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty, isAuthorMutedByViewer = false),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(muteAuthorLabel())
            .performClick()

        assertEquals(PostOverflowAction.MuteAuthor, recorded)
    }

    @Test
    fun overflowMenu_unmuteAuthor_invokesCallbackWithUnmuteAuthor_whenMuted() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty, isAuthorMutedByViewer = true),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(unmuteAuthorLabel())
            .performClick()

        assertEquals(PostOverflowAction.UnmuteAuthor, recorded)
    }

    @Test
    fun overflowMenu_blockAuthor_invokesCallbackWithBlockAuthor_whenNotBlocked() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty, isAuthorBlockedByViewer = false),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(blockAuthorLabel())
            .performClick()

        assertEquals(PostOverflowAction.BlockAuthor, recorded)
    }

    @Test
    fun overflowMenu_unblockAuthor_invokesCallbackWithUnblockAuthor_whenBlocked() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty, isAuthorBlockedByViewer = true),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(unblockAuthorLabel())
            .performClick()

        assertEquals(PostOverflowAction.UnblockAuthor, recorded)
    }

    @Test
    fun overflowMenu_muteThread_invokesCallbackWithMuteThread() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(muteThreadLabel).performClick()

        assertEquals(PostOverflowAction.MuteThread, recorded)
    }

    @Test
    fun overflowMenu_copyPostText_invokesCallbackWithCopyPostText() {
        var recorded: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> recorded = action },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(copyPostTextLabel).performClick()

        assertEquals(PostOverflowAction.CopyPostText, recorded)
    }

    @Test
    fun overflowMenu_muteAuthor_isAbsent_whenAlreadyMuted() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty, isAuthorMutedByViewer = true),
                    callbacks = PostCallbacks(onOverflowAction = { _, _ -> }),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        // Mute @handle should NOT exist when the author is already muted —
        // the menu renders Unmute @handle instead. Belt-and-suspenders for
        // the "exactly one of the pair" invariant.
        composeTestRule
            .onNodeWithText(muteAuthorLabel())
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(unmuteAuthorLabel())
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_blockAuthor_isAbsent_whenAlreadyBlocked() {
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty, isAuthorBlockedByViewer = true),
                    callbacks = PostCallbacks(onOverflowAction = { _, _ -> }),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule
            .onNodeWithText(blockAuthorLabel())
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithText(unblockAuthorLabel())
            .assertIsDisplayed()
    }

    @Test
    fun overflowMenu_passesThePostInstanceToTheCallback() {
        // Independent recording slot for the (post, action) pair — locks
        // that the host receives both halves; oftc.3 / .4 / .5 will need
        // the post identity to issue the right RPC.
        var recordedPost: PostUi? = null
        val target = parentPost(embed = EmbedUi.Empty)
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = target,
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { post, _ -> recordedPost = post },
                        ),
                )
            }
        }

        composeTestRule.onNode(hasClickLabel(moreOptionsLabel), useUnmergedTree = true).performClick()
        composeTestRule.onNodeWithText(reportPostLabel).performClick()

        assertTrue("callback should receive the post it was rendered for", recordedPost === target)
    }

    @Test
    fun overflowMenu_neverFiresUntilMenuOpenedAndItemTapped() {
        // Render — but don't open the menu. The callback must NOT fire
        // from composition alone.
        var fired: PostOverflowAction? = null
        composeTestRule.setContent {
            NubecitaTheme {
                PostCard(
                    post = parentPost(embed = EmbedUi.Empty),
                    callbacks =
                        PostCallbacks(
                            onOverflowAction = { _, action -> fired = action },
                        ),
                )
            }
        }
        assertNull("callback must not fire on mere render", fired)
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

        fun parentPost(
            embed: EmbedUi,
            isAuthorMutedByViewer: Boolean = false,
            isAuthorBlockedByViewer: Boolean = false,
        ): PostUi =
            PostUi(
                id = "at://did:plc:parent/app.bsky.feed.post/p",
                cid = "bafyreiparentcid000000000000000000000000000000000",
                author = PARENT_AUTHOR,
                createdAt = Clock.System.now() - 3.minutes,
                text = PARENT_BODY_TEXT,
                facets = persistentListOf(),
                embed = embed,
                stats = PostStatsUi(),
                viewer =
                    ViewerStateUi(
                        isAuthorMutedByViewer = isAuthorMutedByViewer,
                        isAuthorBlockedByViewer = isAuthorBlockedByViewer,
                    ),
                repostedBy = null,
            )
    }
}
