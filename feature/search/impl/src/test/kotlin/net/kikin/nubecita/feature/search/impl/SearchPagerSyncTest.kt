package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `an outside write during a scroll is followed at settle`() {
        // The pager settled on 1 while the pane had written 2 mid-scroll.
        assertEquals(SettleAction.ScrollToStore, settleAction(page = 1, storeTab = 2, outsideWrite = true))
    }

    @Test
    fun `a settle with no outside write reports the pager page`() {
        assertEquals(SettleAction.ReportPage, settleAction(page = 1, storeTab = 2, outsideWrite = false))
    }

    @Test
    fun `a settle on the stored tab does nothing`() {
        for (outside in listOf(true, false)) {
            assertEquals(SettleAction.None, settleAction(page = 2, storeTab = 2, outsideWrite = outside))
        }
    }

    @Test
    fun `the pager's own report does not trigger a follow`() {
        // The pager reported page 1; the store now holds 1: that is the echo, not an outside write.
        assertFalse(isOutsideWrite(storeTab = 1, lastReportedPage = 1))
        assertTrue(isOutsideWrite(storeTab = 2, lastReportedPage = 1))
        // Nothing reported since the pager last agreed with the store: any change is from outside.
        assertTrue(isOutsideWrite(storeTab = 1, lastReportedPage = null))
    }
}
