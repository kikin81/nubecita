package net.kikin.nubecita.feature.postdetail.impl

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ThreadItem
import net.kikin.nubecita.data.models.ThreadPositionUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCallbacks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The opened post is the first visible row of post detail, on every path the
 * thread can arrive by, with the posts before it above the fold.
 *
 * This is the part of [rememberPinnedThread] no JVM test can reach: it rests on
 * a real `LazyColumn` keeping its first visible row by key when rows are added
 * above it, and on the trailing spacer having its height by the time they are.
 * The screen under test is the real stateless post detail content.
 */
class ThreadPinningInstrumentationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val listState = LazyListState()
    private var items by mutableStateOf<ImmutableList<ThreadItem>>(persistentListOf())

    private fun setThread(
        initial: ImmutableList<ThreadItem>,
        showAncestorsImmediately: Boolean = false,
    ) {
        items = initial
        composeTestRule.setContent {
            NubecitaTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalClock provides FixedClock) {
                    PostDetailScreenContent(
                        state = PostDetailState(items = items, loadStatus = PostDetailLoadStatus.Idle),
                        snackbarHostState = remember { SnackbarHostState() },
                        callbacks = PostCallbacks.None,
                        onBack = {},
                        onRetry = {},
                        onRefresh = {},
                        listState = listState,
                        showAncestorsImmediately = showAncestorsImmediately,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Where the opened post sits, in pixels below the top of the list's
     * content area (the part under the app bar), or null when it is not laid
     * out. Asked of the row itself rather than of "the first visible row":
     * rows scrolled up under the app bar still count as visible.
     */
    private fun focusOffset(): Int? =
        composeTestRule.runOnIdle {
            listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.key == FOCUS.key }
                ?.offset
        }

    private fun assertFocusIsPinned() {
        assertEquals("the opened post should sit at the top of the list", 0, focusOffset())
    }

    @Test
    fun aThreadOpenedInItsMiddleStartsOnTheOpenedPost() {
        // The whole thread at once: a snapshot hit, or a cold load.
        setThread(thread(ancestors = 6, replies = 12))

        assertFocusIsPinned()
    }

    @Test
    fun theEarlierPostsAreAboveTheOpenedPost() {
        setThread(thread(ancestors = 3, replies = 12))
        assertFocusIsPinned()

        composeTestRule.runOnIdle { runBlocking { listState.scrollBy(-200f) } }

        // The list could scroll up by the full amount, so there were rows
        // above; and the row now leading the content area is one of them.
        assertEquals(200, focusOffset())
        val leading =
            composeTestRule.runOnIdle {
                listState.layoutInfo.visibleItemsInfo
                    .first { it.index == listState.firstVisibleItemIndex }
                    .key
            }
        assertTrue("expected an earlier thread post at the top, was $leading", leading.toString().startsWith("ancestor:"))
    }

    @Test
    fun withoutTheHoldBackTheListStartsOnTheFirstPostInstead() {
        // The control: this is what every test here would see if the pin did
        // nothing. It also pins down why the hold-back exists.
        setThread(thread(ancestors = 6, replies = 12), showAncestorsImmediately = true)

        val leading =
            composeTestRule.runOnIdle {
                listState.layoutInfo.visibleItemsInfo
                    .first { it.index == listState.firstVisibleItemIndex }
                    .key
            }
        assertTrue("expected the first post of the thread at the top, was $leading", leading.toString().endsWith("/a0"))
        assertTrue("the opened post should be below the top, was at ${focusOffset()}", (focusOffset() ?: Int.MAX_VALUE) > 0)
    }

    @Test
    fun aThreadThatFirstShowsOnlyTheOpenedPostStaysOnItWhenTheRestArrives() {
        // The focus alone, from the feed's cache; then the fetched thread.
        setThread(persistentListOf(FOCUS))
        assertFocusIsPinned()

        items = thread(ancestors = 6, replies = 12)
        composeTestRule.waitForIdle()

        assertFocusIsPinned()
    }

    @Test
    fun aCachedShortThreadStaysOnTheOpenedPostWhenAShorterRefreshLands() {
        setThread(thread(ancestors = 0, replies = 8))
        assertFocusIsPinned()

        // The refresh finds the earlier posts and fewer replies than before.
        items = thread(ancestors = 4, replies = 1)
        composeTestRule.waitForIdle()

        assertFocusIsPinned()
    }

    @Test
    fun anOpenedPostWithNothingBelowItStillReachesTheTop() {
        // Without the trailing room the list could not scroll this far.
        setThread(thread(ancestors = 6, replies = 0))

        assertFocusIsPinned()
    }

    @Test
    fun aThreadThatStartsAtTheOpenedPostGetsNoExtraRows() {
        val rows = thread(ancestors = 0, replies = 2)
        setThread(rows)

        assertFocusIsPinned()
        assertEquals(rows.size, composeTestRule.runOnIdle { listState.layoutInfo.totalItemsCount })
    }

    private fun thread(
        ancestors: Int,
        replies: Int,
    ): ImmutableList<ThreadItem> =
        buildList {
            repeat(ancestors) { add(ThreadItem.Ancestor(post("a$it", index = it + 1, count = ancestors + 1))) }
            add(FOCUS)
            repeat(replies) { add(ThreadItem.Reply(post = post("r$it"), depth = 1)) }
        }.toImmutableList()

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-04-26T10:00:00Z")

        object FixedClock : Clock {
            override fun now(): Instant = Instant.parse("2026-04-26T12:00:00Z")
        }

        fun post(
            id: String,
            index: Int? = null,
            count: Int? = null,
        ): PostUi =
            PostUi(
                id = "at://did:plc:pin/app.bsky.feed.post/$id",
                cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
                author = AuthorUi(did = "did:plc:pin", handle = "pin.test", displayName = "Pin Test", avatarUrl = null),
                createdAt = CREATED_AT,
                text = "Post $id of a thread used to check where the list opens.",
                facets = persistentListOf(),
                embed = EmbedUi.Empty,
                stats = PostStatsUi(),
                viewer = ViewerStateUi(),
                repostedBy = null,
                threadPosition = ThreadPositionUi.of(index = index?.toLong(), count = count?.toLong()),
            )

        val FOCUS = ThreadItem.Focus(post("focus"))
    }
}
