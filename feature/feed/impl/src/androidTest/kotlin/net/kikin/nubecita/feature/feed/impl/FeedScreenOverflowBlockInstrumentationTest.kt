package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShellNavState
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.moderation.api.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import net.kikin.nubecita.designsystem.R as DesignsystemR

/**
 * Instrumentation pin-down for the Block-account flow: the PostCard
 * overflow's "Block @handle" row, tapped on a feed-side PostCard, emits
 * `FeedEffect.NavigateTo(Block(...))` from the VM. The screen's effect
 * collector routes the key out via its `onNavigateTo: (NavKey) -> Unit`
 * callback — the same host hook `FeedNavigationModule` wires to
 * `LocalMainShellNavState.current.add(key)` in production, and the same key
 * `ModerationNavigationModule`'s `@MainShell` provider resolves to the Block
 * confirmation `ModalBottomSheet`.
 *
 * Mirrors `FeedScreenOverflowReportInstrumentationTest` exactly (direct
 * `MainShellNavState` construction, `@TestInstallIn`-faked feed repository, the
 * `navState.backStack` assertion target). Asserting the Block dialog's own
 * render / confirm → `BlockRepository` call is a `:feature:moderation:impl`
 * concern; here we pin only that the overflow row pushes the right NavKey with
 * the tapped author's DID + handle.
 */
@HiltAndroidTest
class FeedScreenOverflowBlockInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    private lateinit var navState: MainShellNavState

    @Before
    fun setUp() {
        hiltRule.inject()
        navState =
            MainShellNavState(
                startRoute = Feed,
                topLevelKeyState = mutableStateOf(Feed),
                backStacks = mapOf<NavKey, NavBackStack<NavKey>>(Feed to NavBackStack(Feed)),
            )
    }

    @Test
    fun overflowMenu_blockRow_pushesBlockNavKeyWithAuthorIdentity() {
        val moreOptionsLabel =
            composeTestRule.activity.getString(DesignsystemR.string.postcard_action_more)
        // moderation_action_block_author is formatted ("Block @%1$s"); the first
        // post's author is alice, so the visible row reads "Block @alice.bsky.social".
        val blockAuthorLabel =
            composeTestRule.activity.getString(DesignsystemR.string.moderation_action_block_author, POST_ALICE_HANDLE)

        composeTestRule.setContent {
            // FeedScreen's shared rememberPostInteractions helper reads
            // LocalMainShellNavState unconditionally (error() default), so it must
            // be provided or the first composition crashes before any post renders.
            NubecitaTheme {
                CompositionLocalProvider(LocalMainShellNavState provides navState) {
                    FeedScreen(onNavigateTo = { key -> navState.add(key) })
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasText(POST_ALICE_TEXT, substring = true))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        assertEquals(listOf<NavKey>(Feed), navState.backStack.toList())

        composeTestRule
            .onAllNodes(hasClickLabel(moreOptionsLabel), useUnmergedTree = true)[0]
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(blockAuthorLabel).performClick()

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            navState.backStack.any { it is Block }
        }

        val pushed = navState.backStack.last()
        assertTrue(
            "expected last back-stack key to be a Block sub-route, got $pushed",
            pushed is Block,
        )
        pushed as Block
        assertEquals(POST_ALICE_DID, pushed.did)
        assertEquals(POST_ALICE_HANDLE, pushed.handle)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L

        // PostStat labels non-toggleable action cells (incl. the overflow) via
        // onClickLabel, NOT contentDescription — so match the OnClick action label.
        fun hasClickLabel(label: String) =
            SemanticsMatcher("OnClick action label == '$label'") { node ->
                node.config.getOrNull(SemanticsActions.OnClick)?.label == label
            }

        // Mirror of FakeFeedRepository.DEFAULT_TIMELINE's first post (alice).
        // singlePost derives the author DID from the post id's authority segment
        // (`at://did:plc:alice/...`), so the DID is did:plc:alice.
        const val POST_ALICE_TEXT = "Hello world from alice"
        const val POST_ALICE_DID = "did:plc:alice"
        const val POST_ALICE_HANDLE = "alice.bsky.social"
    }
}
