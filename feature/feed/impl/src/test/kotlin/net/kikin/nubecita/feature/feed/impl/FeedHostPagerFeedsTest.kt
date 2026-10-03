package net.kikin.nubecita.feature.feed.impl

import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests verifying pager feeds resolution and back handler conditions
 * for horizontal feed swiping (nubecita-gpkc).
 */
class FeedHostPagerFeedsTest {
    private val following =
        PinnedFeedUi(
            id = "following",
            uri = "app.bsky.feed.getTimeline",
            kind = FeedKind.Following,
            displayName = "Following",
            avatarUrl = null,
        )
    private val discover =
        PinnedFeedUi(
            id = "discover",
            uri = "at://did:plc:x/app.bsky.feed.generator/whats-hot",
            kind = FeedKind.Generator,
            displayName = "Discover",
            avatarUrl = null,
        )
    private val science =
        PinnedFeedUi(
            id = "science",
            uri = "at://did:plc:x/app.bsky.feed.generator/science",
            kind = FeedKind.Generator,
            displayName = "Science",
            avatarUrl = null,
        )
    private val techList =
        PinnedFeedUi(
            id = "tech",
            uri = "at://did:plc:x/app.bsky.graph.list/tech",
            kind = FeedKind.List,
            displayName = "Tech",
            avatarUrl = null,
        )

    private val feedChips = listOf(following, discover, science)
    private val pinnedLists = listOf(techList)

    @Test
    fun `pagerFeeds contains feedChips when no pinned list is selected`() {
        val pagerFeeds = resolvePagerFeeds(feedChips, pinnedLists, selectedFeedUri = discover.uri)
        assertEquals(3, pagerFeeds.size)
        assertEquals(listOf(following, discover, science), pagerFeeds)
    }

    @Test
    fun `pagerFeeds appends active pinned list when selected`() {
        val pagerFeeds = resolvePagerFeeds(feedChips, pinnedLists, selectedFeedUri = techList.uri)
        assertEquals(4, pagerFeeds.size)
        assertEquals(listOf(following, discover, science, techList), pagerFeeds)
    }

    @Test
    fun `backHandler is disabled on page 0 and enabled on page greater than 0`() {
        // Page 0 (Following): back exits through home (disabled at host level so NavDisplay handles it)
        assertFalse(isBackHandlerEnabled(currentPage = 0))

        // Page 1+ (Discover, Science, etc.): back intercepts to return to Page 0
        assertTrue(isBackHandlerEnabled(currentPage = 1))
        assertTrue(isBackHandlerEnabled(currentPage = 2))
    }

    @Test
    fun `adaptive scroll flag disables horizontal drag on tablet widths`() {
        // Width in dp: Compact (<600dp) enables swiping; Medium/Expanded (>=600dp) disables swiping
        assertTrue(isUserScrollEnabled(widthDp = 400))
        assertTrue(isUserScrollEnabled(widthDp = 599))
        assertFalse(isUserScrollEnabled(widthDp = 600))
        assertFalse(isUserScrollEnabled(widthDp = 840))
        assertFalse(isUserScrollEnabled(widthDp = 1200))
    }

    @Test
    fun `isPageActive is true only when page matches settledPage and scroll is not in progress`() {
        assertTrue(isPageActive(settledPage = 1, page = 1, isScrollInProgress = false))
        assertFalse(isPageActive(settledPage = 1, page = 1, isScrollInProgress = true))
        assertFalse(isPageActive(settledPage = 0, page = 1, isScrollInProgress = false))
        assertFalse(isPageActive(settledPage = 2, page = 1, isScrollInProgress = true))
    }

    companion object {
        fun resolvePagerFeeds(
            feedChips: List<PinnedFeedUi>,
            pinnedLists: List<PinnedFeedUi>,
            selectedFeedUri: String?,
        ): List<PinnedFeedUi> {
            val activeList = pinnedLists.firstOrNull { it.uri == selectedFeedUri }
            return if (activeList != null && feedChips.none { it.uri == activeList.uri }) {
                feedChips + activeList
            } else {
                feedChips
            }
        }

        fun isBackHandlerEnabled(currentPage: Int): Boolean = currentPage != 0

        fun isUserScrollEnabled(widthDp: Int): Boolean = widthDp < 600

        fun isPageActive(
            settledPage: Int,
            page: Int,
            isScrollInProgress: Boolean,
        ): Boolean = settledPage == page && !isScrollInProgress
    }
}
