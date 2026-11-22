package net.kikin.nubecita.feature.search.impl

import net.kikin.nubecita.feature.search.impl.data.SearchTab
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Plays the part of the composable driver around [TabSyncState]: it holds the stored tab and the
 * page the pager was last asked to show, applies each action the machine returns, and feeds the
 * store's own echo of a [TabSyncAction.Report] back as an observation, as the real effect does.
 */
private class Driver(
    startTab: SearchTab,
) {
    var state = TabSyncState()
    var store = startTab
    var requestedPage = startTab
    val actions = mutableListOf<TabSyncAction>()

    private fun apply(step: TabSyncStep) {
        state = step.state
        actions += step.action
        when (val action = step.action) {
            TabSyncAction.None -> Unit
            is TabSyncAction.ScrollTo -> requestedPage = action.tab
            is TabSyncAction.Report -> {
                store = action.tab
                observeStore()
            }
        }
    }

    private fun observeStore() = apply(state.onStoreTabObserved(store, pagerTarget = requestedPage))

    /** A tab written from outside the pager: the pane's "See all" or a tap on the tab row. */
    fun request(tab: SearchTab) {
        store = tab
        observeStore()
    }

    fun dragStarted() {
        state = state.onUserDragStarted()
    }

    /** The pager is idle on [page]. */
    fun settle(page: SearchTab) {
        requestedPage = page
        apply(state.onSettled(page, store))
    }
}

class SearchPagerSyncTest {
    @Test
    fun `a swipe with no request is reported once, its echo is consumed and nothing else happens`() {
        val d = Driver(SearchTab.POSTS)
        d.settle(SearchTab.PEOPLE)

        assertEquals(SearchTab.PEOPLE, d.store)
        assertEquals(SearchTab.PEOPLE, d.requestedPage)
        assertEquals(listOf(TabSyncAction.Report(SearchTab.PEOPLE), TabSyncAction.None), d.actions)
        assertEquals(TabSyncState(), d.state)
    }

    @Test
    fun `a request while idle scrolls there and the settle needs no report`() {
        val d = Driver(SearchTab.POSTS)
        d.request(SearchTab.FEEDS)
        assertEquals(listOf<TabSyncAction>(TabSyncAction.ScrollTo(SearchTab.FEEDS)), d.actions)

        d.settle(SearchTab.FEEDS)

        assertEquals(SearchTab.FEEDS, d.store)
        assertEquals(SearchTab.FEEDS, d.requestedPage)
        assertEquals(listOf(TabSyncAction.ScrollTo(SearchTab.FEEDS), TabSyncAction.None), d.actions)
        assertEquals(TabSyncState(), d.state)
    }

    @Test
    fun `a request made after a drag began is followed again when the drag settles elsewhere`() {
        val d = Driver(SearchTab.POSTS)
        d.dragStarted() // no request pending yet: the drag means nothing to the machine
        d.request(SearchTab.FEEDS) // blocked by the drag; the pager is heading to People
        d.settle(SearchTab.PEOPLE)
        assertEquals(SearchTab.FEEDS, d.requestedPage)

        d.settle(SearchTab.FEEDS)

        assertEquals(SearchTab.FEEDS, d.store)
        assertEquals(SearchTab.FEEDS, d.requestedPage)
        assertEquals(TabSyncState(), d.state)
        assertEquals(0, d.actions.count { it is TabSyncAction.Report })
    }

    @Test
    fun `a tap during a programmatic scroll wins and the older request is not retried`() {
        val d = Driver(SearchTab.POSTS)
        d.request(SearchTab.FEEDS) // ScrollTo(Feeds) is running
        d.request(SearchTab.PEOPLE) // the user taps People
        assertEquals(SearchTab.PEOPLE, d.requestedPage)

        d.settle(SearchTab.PEOPLE)

        assertEquals(SearchTab.PEOPLE, d.store)
        assertEquals(SearchTab.PEOPLE, d.requestedPage)
        assertEquals(
            listOf<TabSyncAction>(
                TabSyncAction.ScrollTo(SearchTab.FEEDS),
                TabSyncAction.ScrollTo(SearchTab.PEOPLE),
                TabSyncAction.None,
            ),
            d.actions,
        )
    }

    @Test
    fun `two requests in one scroll end on the last one`() {
        val d = Driver(SearchTab.POSTS)
        d.request(SearchTab.PEOPLE)
        d.request(SearchTab.FEEDS)

        d.settle(SearchTab.FEEDS)

        assertEquals(SearchTab.FEEDS, d.store)
        assertEquals(SearchTab.FEEDS, d.requestedPage)
        assertEquals(TabSyncState(), d.state)
    }

    @Test
    fun `a drag after a request wins and is reported`() {
        val d = Driver(SearchTab.POSTS)
        d.request(SearchTab.FEEDS)
        d.dragStarted()

        d.settle(SearchTab.PEOPLE)

        assertEquals(SearchTab.PEOPLE, d.store)
        assertEquals(SearchTab.PEOPLE, d.requestedPage)
        assertEquals(TabSyncAction.Report(SearchTab.PEOPLE), d.actions.last { it is TabSyncAction.Report })
        assertEquals(TabSyncState(), d.state)
    }

    @Test
    fun `a genuine outside request equal to an earlier report is followed once that echo was consumed`() {
        val d = Driver(SearchTab.POSTS)
        d.settle(SearchTab.PEOPLE) // reported; echo consumed
        d.settle(SearchTab.FEEDS) // the user swiped on to Feeds; reported

        d.request(SearchTab.PEOPLE) // the pane's "See all" asks for People

        assertEquals(SearchTab.PEOPLE, d.requestedPage)
        assertEquals(TabSyncAction.ScrollTo(SearchTab.PEOPLE), d.actions.last())
        d.settle(SearchTab.PEOPLE)
        assertEquals(SearchTab.PEOPLE, d.store)
    }

    @Test
    fun `a settle after an interrupted scroll is still processed`() {
        val d = Driver(SearchTab.POSTS)
        d.request(SearchTab.FEEDS) // this scroll is cut short and its job ends; the machine is unaffected

        d.dragStarted()
        d.settle(SearchTab.PEOPLE)
        d.settle(SearchTab.POSTS) // and the next swipe is reported as well

        assertEquals(SearchTab.POSTS, d.store)
        assertEquals(listOf(SearchTab.PEOPLE, SearchTab.POSTS), d.actions.filterIsInstance<TabSyncAction.Report>().map { it.tab })
    }

    @Test
    fun `swiping through all four tabs reports each page in turn and a request scrolls back`() {
        assertEquals(listOf(SearchTab.POSTS, SearchTab.PEOPLE, SearchTab.FEEDS, SearchTab.PACKS), SearchTab.entries.toList())
        val d = Driver(SearchTab.POSTS)

        d.settle(SearchTab.PEOPLE)
        d.settle(SearchTab.FEEDS)
        d.settle(SearchTab.PACKS)

        assertEquals(SearchTab.PACKS, d.store)
        assertEquals(
            listOf(
                TabSyncAction.Report(SearchTab.PEOPLE),
                TabSyncAction.None,
                TabSyncAction.Report(SearchTab.FEEDS),
                TabSyncAction.None,
                TabSyncAction.Report(SearchTab.PACKS),
                TabSyncAction.None,
            ),
            d.actions,
        )

        // "See all" on the Summary's packs section, then a request back to People from the tab row.
        d.actions.clear()
        d.request(SearchTab.PEOPLE)
        d.settle(SearchTab.PEOPLE)
        assertEquals(listOf(TabSyncAction.ScrollTo(SearchTab.PEOPLE), TabSyncAction.None), d.actions)
        d.actions.clear()
        d.request(SearchTab.PACKS)
        d.settle(SearchTab.PACKS)
        assertEquals(listOf(TabSyncAction.ScrollTo(SearchTab.PACKS), TabSyncAction.None), d.actions)
        assertEquals(TabSyncState(), d.state)
    }
}
