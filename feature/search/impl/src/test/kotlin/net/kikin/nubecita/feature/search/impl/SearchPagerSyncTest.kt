package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchPagerSyncTest {
    @Test
    fun `the pager follows the stored tab only when it differs from the target and nothing is scrolling`() {
        for (storeTab in 0..2) {
            for (pagerTarget in 0..2) {
                for (scrolling in listOf(true, false)) {
                    val expected = storeTab != pagerTarget && !scrolling
                    assertEquals(
                        expected,
                        shouldFollowTab(storeTab = storeTab, pagerTarget = pagerTarget, isScrollInProgress = scrolling),
                        "store=$storeTab target=$pagerTarget scrolling=$scrolling",
                    )
                }
            }
        }
    }

    @Test
    fun `a settled page is reported only when it is not what the store already holds`() {
        for (page in 0..2) {
            for (storeTab in 0..2) {
                for (scrolling in listOf(true, false)) {
                    val expected = page != storeTab && !scrolling
                    assertEquals(
                        expected,
                        shouldReportSettledPage(page = page, storeTab = storeTab, isScrollInProgress = scrolling),
                        "page=$page store=$storeTab scrolling=$scrolling",
                    )
                }
            }
        }
    }
}
