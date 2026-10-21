package net.kikin.nubecita.feature.videos.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.kikin.nubecita.core.postinteractions.ui.comments.rememberVideoCommentsNestedScrollConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test verifying gesture arbitration between the video feed's parent
 * [VerticalPager] and the adaptive comments sheet (OpenSpec D1 & Task 1.5).
 *
 * Verifies:
 * - When comments sheet is open / not settled hidden (`isSheetSettledHidden == false`),
 *   `userScrollEnabled` on [VerticalPager] is false. Swiping over comments does not
 *   scroll or advance the parent [VerticalPager].
 * - [rememberVideoCommentsNestedScrollConnection] consumes upward deltas when partially
 *   expanded (triggering expand) and downward deltas at scroll offset 0 (triggering collapse)
 *   without dispatching scroll deltas to the pager.
 * - Only after the sheet has completely settled in hidden (`isSheetSettledHidden == true`)
 *   is [VerticalPager.userScrollEnabled] re-enabled, allowing normal page swipes.
 */
@RunWith(AndroidJUnit4::class)
class VideoFeedGestureArbitrationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun draggingComments_whenSheetIsOpen_doesNotScrollParentVerticalPager() {
        var isCommentsOpen by mutableStateOf(true)
        var isSheetSettledHidden by mutableStateOf(false)
        var expandedTriggered by mutableStateOf(false)
        var collapseTriggered by mutableStateOf(false)
        lateinit var pagerState: PagerState

        composeTestRule.setContent {
            pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

            Box(Modifier.fillMaxSize()) {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = isSheetSettledHidden,
                    modifier = Modifier.fillMaxSize().testTag(VideoFeedTestTags.PAGER),
                ) { page ->
                    Box(Modifier.fillMaxSize()) {
                        Text("Video Page $page")

                        if (page == 0 && isCommentsOpen) {
                            val listState = rememberLazyListState()
                            val nestedScroll =
                                rememberVideoCommentsNestedScrollConnection(
                                    isPartiallyExpanded = { true },
                                    isAtTop = {
                                        listState.firstVisibleItemIndex == 0 &&
                                            listState.firstVisibleItemScrollOffset == 0
                                    },
                                    onExpand = { expandedTriggered = true },
                                    onCollapse = { collapseTriggered = true },
                                )

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(300.dp)
                                        .align(Alignment.BottomCenter)
                                        .background(Color.DarkGray)
                                        .nestedScroll(nestedScroll)
                                        .testTag("comments_sheet"),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().testTag("comments_list"),
                                ) {
                                    items(30) { index ->
                                        Text("Comment $index", Modifier.height(40.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Assert initial state is page 0
        composeTestRule.waitForIdle()
        assertEquals(0, pagerState.currentPage)

        // 1. Sheet is open and NOT settled hidden: swipe up over the comments sheet
        composeTestRule.onNodeWithTag("comments_sheet").performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()

        // Pager MUST NOT have scrolled: userScrollEnabled was false and nested scroll consumed
        assertEquals(0, pagerState.currentPage)
        assertTrue("Nested scroll should trigger onExpand on upward swipe", expandedTriggered)

        // 2. Swipe down over comments sheet at top triggers collapse, but pager still stays at page 0
        composeTestRule.onNodeWithTag("comments_sheet").performTouchInput {
            swipeDown()
        }
        composeTestRule.waitForIdle()

        assertEquals(0, pagerState.currentPage)
        assertTrue("Nested scroll should trigger onCollapse on downward swipe at top", collapseTriggered)

        // 3. Now simulate the sheet being closed and completely settled to hidden
        isCommentsOpen = false
        isSheetSettledHidden = true
        composeTestRule.waitForIdle()

        // Pager scrolling is now re-enabled: swiping up on the pager advances to page 1
        composeTestRule.onNodeWithTag(VideoFeedTestTags.PAGER).performTouchInput {
            swipeUp()
        }
        composeTestRule.waitForIdle()

        // Pager has cleanly scrolled to page 1
        assertEquals(1, pagerState.currentPage)
    }
}
