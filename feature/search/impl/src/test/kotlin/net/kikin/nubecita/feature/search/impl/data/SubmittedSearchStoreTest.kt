package net.kikin.nubecita.feature.search.impl.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubmittedSearchStoreTest {
    private val store = SubmittedSearchStore()

    @Test
    fun `submit publishes query with default filters`() {
        store.submit("kotlin", fromRecent = false)

        val submitted = store.submitted.value
        assertEquals("kotlin", submitted?.query)
        assertEquals(false, submitted?.fromRecent)
        assertEquals(PostSearchFilters(), submitted?.filters)
        assertEquals(SearchTab.POSTS, submitted?.selectedTab)
        assertEquals(false, submitted?.isFilterSheetOpen)
        assertEquals(1, submitted?.submission)
    }

    @Test
    fun `a second submit keeps filters and tab`() {
        store.submit("kotlin", fromRecent = false)
        store.updateFilters { it.copy(followingOnly = true) }
        store.selectTab(SearchTab.FEEDS)

        store.submit("compose", fromRecent = true)

        val submitted = store.submitted.value
        assertEquals("compose", submitted?.query)
        assertEquals(true, submitted?.fromRecent)
        assertEquals(true, submitted?.filters?.followingOnly)
        assertEquals(SearchTab.FEEDS, submitted?.selectedTab)
    }

    @Test
    fun `a second submit of the same query increments submission`() {
        store.submit("kotlin", fromRecent = false)
        val first = store.submitted.value?.submission

        store.submit("kotlin", fromRecent = false)

        assertEquals(1, first)
        assertEquals(2, store.submitted.value?.submission)
    }

    @Test
    fun `submission keeps counting after clear`() {
        store.submit("kotlin", fromRecent = false)
        store.clear()

        store.submit("kotlin", fromRecent = false)

        assertEquals(2, store.submitted.value?.submission)
    }

    @Test
    fun `clear resets to null and the next submit has default filters`() {
        store.submit("kotlin", fromRecent = false)
        store.updateFilters { it.copy(followingOnly = true) }
        store.selectTab(SearchTab.PEOPLE)
        store.setFilterSheetOpen(true)

        store.clear()
        assertNull(store.submitted.value)

        store.submit("kotlin", fromRecent = false)
        assertEquals(PostSearchFilters(), store.submitted.value?.filters)
        assertEquals(SearchTab.POSTS, store.submitted.value?.selectedTab)
        assertEquals(false, store.submitted.value?.isFilterSheetOpen)
    }

    @Test
    fun `updateFilters, selectTab and setFilterSheetOpen are no-ops when nothing is submitted`() {
        store.updateFilters { it.copy(followingOnly = true) }
        store.selectTab(SearchTab.FEEDS)
        store.setFilterSheetOpen(true)

        assertNull(store.submitted.value)
    }
}
