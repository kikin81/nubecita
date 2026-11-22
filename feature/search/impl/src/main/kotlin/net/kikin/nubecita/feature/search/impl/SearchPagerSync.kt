package net.kikin.nubecita.feature.search.impl

/**
 * Whether the results pager should animate to the stored tab.
 *
 * The stored tab (`SubmittedSearchStore`) and the pager page are two views of the same fact
 * with writers on both sides: the pager reports a page the user settled on, and the second
 * pane's "See all" writes the tab. Following is only right when the pager is idle and not
 * already heading to that page: a scroll in flight (a tab tap, a swipe, a fling) owns the
 * pager until it settles, and the settle is then reported back, so starting another animation
 * would cancel it and the two sides would chase each other.
 */
internal fun shouldFollowTab(
    storeTab: Int,
    pagerTarget: Int,
    isScrollInProgress: Boolean,
): Boolean = !isScrollInProgress && storeTab != pagerTarget

/**
 * Whether a page the pager shows should be written to the store. Never mid-scroll (a
 * programmatic scroll passes through intermediate pages that nobody chose) and never when
 * the store already holds it (that would only echo the store's own write back to it).
 */
internal fun shouldReportSettledPage(
    page: Int,
    storeTab: Int,
    isScrollInProgress: Boolean,
): Boolean = !isScrollInProgress && page != storeTab
