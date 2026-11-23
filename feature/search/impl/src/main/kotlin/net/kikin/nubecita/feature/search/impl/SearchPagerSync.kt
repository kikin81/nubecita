package net.kikin.nubecita.feature.search.impl

import net.kikin.nubecita.feature.search.impl.data.SearchTab

/**
 * What the results pager's driver must do next.
 */
internal sealed interface TabSyncAction {
    data object None : TabSyncAction

    /** Animate the pager to [tab]. Run in its own job: an interrupted animation must end only that job. */
    data class ScrollTo(
        val tab: SearchTab,
    ) : TabSyncAction

    /** Write [tab], the page the user reached by dragging, to the store. */
    data class Report(
        val tab: SearchTab,
    ) : TabSyncAction
}

/** The new state and the action that goes with an event. */
internal data class TabSyncStep(
    val state: TabSyncState,
    val action: TabSyncAction,
)

/**
 * The one rule set that keeps the results pager and the stored tab (`SubmittedSearchStore`) in
 * step. Two kinds of tab choice exist. A drag is the pager's own: once it settles, the page is
 * reported to the store. Everything else is a REQUEST written to the store (the second pane's
 * "See all", a tap on the Search screen's tab row): the pager scrolls to it. A request is
 * [pending] until the pager reaches it, and is retried if something older (a drag that began
 * before it) lands the pager elsewhere; but a drag that began AFTER it is the user's newer
 * choice and wins.
 *
 * Pure: the composable only feeds events in and carries the returned actions out.
 *
 * @property pending a requested tab the pager has not reached yet.
 * @property dragSinceRequest the user started a drag after [pending] was requested.
 * @property expectEcho a tab this screen itself just wrote to the store; when the store reports it
 *   back it is not a request.
 */
internal data class TabSyncState(
    val pending: SearchTab? = null,
    val dragSinceRequest: Boolean = false,
    val expectEcho: SearchTab? = null,
)

/** The stored tab is [tab]; the pager is currently heading for [pagerTarget] (or sitting on it). */
internal fun TabSyncState.onStoreTabObserved(
    tab: SearchTab,
    pagerTarget: SearchTab,
): TabSyncStep {
    if (tab == expectEcho) return TabSyncStep(copy(expectEcho = null), TabSyncAction.None)
    if (pagerTarget == tab) return TabSyncStep(TabSyncState(), TabSyncAction.None)
    return TabSyncStep(TabSyncState(pending = tab), TabSyncAction.ScrollTo(tab))
}

/** The user started dragging the pager. Only a drag after a request matters. */
internal fun TabSyncState.onUserDragStarted(): TabSyncState = if (pending != null) copy(dragSinceRequest = true) else this

/** The pager is idle on [page] while the store holds [storeTab]. */
internal fun TabSyncState.onSettled(
    page: SearchTab,
    storeTab: SearchTab,
): TabSyncStep {
    val request = pending
    return when {
        request == page -> TabSyncStep(copy(pending = null, dragSinceRequest = false), TabSyncAction.None)
        // The request was blocked or interrupted by something older: ask again.
        request != null && !dragSinceRequest -> TabSyncStep(this, TabSyncAction.ScrollTo(request))
        // No request (or the user's drag is newer than it): the page is the truth.
        page != storeTab -> TabSyncStep(TabSyncState(expectEcho = page), TabSyncAction.Report(page))
        else -> TabSyncStep(TabSyncState(), TabSyncAction.None)
    }
}

/** Holds the machine's state across effects; plain field, only touched from the main thread. */
internal class TabSyncHolder {
    var state = TabSyncState()
}
