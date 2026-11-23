package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExplorePaneLogicTest {
    private fun body(
        feeds: DiscoverSectionStatus,
        feedsHasItems: Boolean = false,
        accounts: DiscoverSectionStatus,
        accountsHasItems: Boolean = false,
        packs: DiscoverSectionStatus = accounts,
        packsHaveItems: Boolean = accountsHasItems,
    ) = exploreBodyFor(feeds, feedsHasItems, accounts, accountsHasItems, packs, packsHaveItems)

    @Test
    fun `all failed shows retry`() {
        assertEquals(
            ExploreBody.Retry,
            body(DiscoverSectionStatus.Error, accounts = DiscoverSectionStatus.Error),
        )
    }

    @Test
    fun `empty and failed shows the nothing message not the retry`() {
        assertEquals(
            ExploreBody.Nothing,
            body(DiscoverSectionStatus.Empty, accounts = DiscoverSectionStatus.Error),
        )
        assertEquals(
            ExploreBody.Nothing,
            body(DiscoverSectionStatus.Empty, accounts = DiscoverSectionStatus.Empty),
        )
    }

    @Test
    fun `a loaded section keeps the content and hides the failed one`() {
        assertEquals(
            ExploreBody.Content,
            body(DiscoverSectionStatus.Loaded, feedsHasItems = true, accounts = DiscoverSectionStatus.Error),
        )
    }

    @Test
    fun `a section still loading keeps the content`() {
        assertEquals(
            ExploreBody.Content,
            body(DiscoverSectionStatus.Loading, accounts = DiscoverSectionStatus.Error),
        )
        assertEquals(
            ExploreBody.Content,
            body(DiscoverSectionStatus.Idle, accounts = DiscoverSectionStatus.Idle),
        )
    }

    @Test
    fun `starter packs count as a section`() {
        // Feeds and people failed, packs loaded: there is content, not a retry.
        assertEquals(
            ExploreBody.Content,
            body(DiscoverSectionStatus.Error, accounts = DiscoverSectionStatus.Error, packs = DiscoverSectionStatus.Loaded, packsHaveItems = true),
        )
        // Packs still loading keeps the content even when the other two failed.
        assertEquals(
            ExploreBody.Content,
            body(DiscoverSectionStatus.Error, accounts = DiscoverSectionStatus.Error, packs = DiscoverSectionStatus.Loading),
        )
        // Packs are the only section that did not fail: nothing to suggest, not a retry.
        assertEquals(
            ExploreBody.Nothing,
            body(DiscoverSectionStatus.Error, accounts = DiscoverSectionStatus.Error, packs = DiscoverSectionStatus.Empty),
        )
    }

    @Test
    fun `feed columns switch at 600dp`() {
        assertEquals(1, feedColumnsFor(480.dp))
        assertEquals(1, feedColumnsFor(599.dp))
        assertEquals(2, feedColumnsFor(600.dp))
        assertEquals(2, feedColumnsFor(700.dp))
    }
}
