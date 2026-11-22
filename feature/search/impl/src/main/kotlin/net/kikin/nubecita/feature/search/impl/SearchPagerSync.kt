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

/** What to do when the pager has settled (is idle) on a page. */
internal enum class SettleAction {
    /** The pager and the store agree. */
    None,

    /** Someone else wrote a tab while the pager was busy: scroll to it, the write wins. */
    ScrollToStore,

    /** Nobody else wrote: the user's page is the truth, write it to the store. */
    ReportPage,
}

/**
 * The decision at settle. [outsideWrite] is true when the store's tab was written by a surface
 * other than the pager (see [isOutsideWrite]) and the pager has not shown it yet; such a write
 * made while a scroll was running (so [shouldFollowTab] declined) must still win. Intermediate
 * pages of a programmatic scroll never reach this: it is only asked once the pager is idle.
 */
internal fun settleAction(
    page: Int,
    storeTab: Int,
    outsideWrite: Boolean,
): SettleAction =
    when {
        page == storeTab -> SettleAction.None
        outsideWrite -> SettleAction.ScrollToStore
        else -> SettleAction.ReportPage
    }

/**
 * Whether the store's [storeTab] is a write from outside the pager. The pager's own report
 * ([lastReportedPage], the page it last wrote; null once it has agreed with the store again)
 * comes back through the store as a tab change too, and that echo is not an outside write.
 */
internal fun isOutsideWrite(
    storeTab: Int,
    lastReportedPage: Int?,
): Boolean = storeTab != lastReportedPage

/** The results pager's memory between two effects; plain fields, only touched from the main thread. */
internal class PagerTabSync {
    /** The page the pager last wrote to the store, or null once it agreed with the store again. */
    var lastReportedPage: Int? = null

    /** The store holds a tab written from outside that the pager has not shown yet. */
    var outsideWrite: Boolean = false
}
