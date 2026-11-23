package net.kikin.nubecita.feature.search.impl.pane

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.SearchTab
import net.kikin.nubecita.feature.search.impl.data.SubmittedSearchStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class SearchPaneViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val store = SubmittedSearchStore()

    private fun buildVm() = SearchPaneViewModel(store)

    @Test
    fun `mode is Explore with no search`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            assertEquals(SearchPaneMode.Explore, vm.uiState.value.mode)
        }

    @Test
    fun `mode is Summary after a submit`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            val mode = vm.uiState.value.mode
            assertTrue(mode is SearchPaneMode.Summary)
            assertEquals("kotlin", (mode as SearchPaneMode.Summary).search.query)
        }

    @Test
    fun `mode returns to Explore after clear`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            store.clear()
            advanceUntilIdle()

            assertEquals(SearchPaneMode.Explore, vm.uiState.value.mode)
        }

    @Test
    fun `ScrollSaved stores under the current mode only`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(3, 40)))
            assertEquals(ScrollPosition(3, 40), vm.uiState.value.exploreScroll)
            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            assertEquals(ScrollPosition(3, 40), vm.uiState.value.exploreScroll)
            assertEquals(ScrollPosition(7, 5), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `ScrollSaved with an explicit target stores under that mode even when the mode has moved on`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()

            // The Explore list leaving composition after a search was submitted.
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(2, 9), ScrollTarget.Explore))

            assertEquals(ScrollPosition(2, 9), vm.uiState.value.exploreScroll)
            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `explore scroll survives a search and its clearing`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(3, 40)))

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            store.clear()
            advanceUntilIdle()

            assertEquals(ScrollPosition(3, 40), vm.uiState.value.exploreScroll)
        }

    @Test
    fun `summary scroll resets when the query changes`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `summary scroll resets when a filter changes`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            store.updateFilters { it.copy(media = MediaFilter.HAS_MEDIA) }
            advanceUntilIdle()

            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `summary scroll is kept when only the selected tab changes`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(7, 5)))

            store.selectTab(SearchTab.PEOPLE)
            advanceUntilIdle()

            assertEquals(ScrollPosition(7, 5), vm.uiState.value.summaryScroll)
        }

    @Test
    fun `section expansion toggles flip independently and survive a mode change`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()

            vm.handleEvent(SearchPaneEvent.FeedsExpansionToggled)
            assertTrue(vm.uiState.value.feedsExpanded)
            assertEquals(false, vm.uiState.value.accountsExpanded)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            store.clear()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.feedsExpanded)

            vm.handleEvent(SearchPaneEvent.FeedsExpansionToggled)
            vm.handleEvent(SearchPaneEvent.AccountsExpansionToggled)
            assertEquals(false, vm.uiState.value.feedsExpanded)
            assertTrue(vm.uiState.value.accountsExpanded)
        }

    @Test
    fun `the summary key changes with the query and with the filters but not with the selected tab`() =
        runTest {
            val vm = buildVm()
            advanceUntilIdle()
            assertEquals(null, vm.uiState.value.summaryKey)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            val first = vm.uiState.value.summaryKey
            assertEquals("kotlin", first?.query)

            store.selectTab(SearchTab.PEOPLE)
            store.setFilterSheetOpen(true)
            advanceUntilIdle()
            assertEquals(first, vm.uiState.value.summaryKey)

            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            assertEquals(first, vm.uiState.value.summaryKey)

            store.updateFilters { it.copy(media = MediaFilter.HAS_MEDIA) }
            advanceUntilIdle()
            val afterFilter = vm.uiState.value.summaryKey
            assertTrue(afterFilter != first)

            store.submit("compose", fromRecent = false)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.summaryKey != afterFilter)

            store.clear()
            advanceUntilIdle()
            assertEquals(null, vm.uiState.value.summaryKey)
        }

    @Test
    fun `a summary scroll save for a stale key is ignored`() =
        runTest {
            val vm = buildVm()
            store.submit("kotlin", fromRecent = false)
            advanceUntilIdle()
            val staleKey = vm.uiState.value.summaryKey
            store.submit("compose", fromRecent = false)
            advanceUntilIdle()

            // The old list leaving composition after the search changed must not undo the reset.
            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(9, 9), ScrollTarget.Summary, staleKey))
            assertEquals(ScrollPosition(), vm.uiState.value.summaryScroll)

            vm.handleEvent(SearchPaneEvent.ScrollSaved(ScrollPosition(4, 2), ScrollTarget.Summary, vm.uiState.value.summaryKey))
            assertEquals(ScrollPosition(4, 2), vm.uiState.value.summaryScroll)
        }
}
