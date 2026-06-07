package net.kikin.nubecita.feature.feed.impl.ui

import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [selectedFeedChipIndex] — the chip-row scroll-target logic
 * (nubecita-62v9). Verifies the row scrolls the active feed/list chip to the
 * start instead of resetting to index 0 on a feed switch.
 */
class FeedChipRowIndexTest {
    private val following = chip("at://following", FeedKind.Following, "Following")
    private val discover = chip("at://discover", FeedKind.Generator, "Discover")
    private val science = chip("at://science", FeedKind.Generator, "Science")
    private val feedChips = listOf(following, discover, science)

    private val listA = chip("at://list/a", FeedKind.List, "Mutuals")
    private val listB = chip("at://list/b", FeedKind.List, "News")
    private val pinnedLists = listOf(listA, listB)

    @Test
    fun selectedFeedChip_returnsItsIndex() {
        assertEquals(0, selectedFeedChipIndex(feedChips, pinnedLists, following.uri))
        assertEquals(1, selectedFeedChipIndex(feedChips, pinnedLists, discover.uri))
        assertEquals(2, selectedFeedChipIndex(feedChips, pinnedLists, science.uri))
    }

    @Test
    fun selectedPinnedList_returnsDisclosureChipIndex() {
        // Any pinned list maps to the single disclosure chip after the feed chips.
        assertEquals(feedChips.size, selectedFeedChipIndex(feedChips, pinnedLists, listA.uri))
        assertEquals(feedChips.size, selectedFeedChipIndex(feedChips, pinnedLists, listB.uri))
    }

    @Test
    fun nullSelection_returnsMinusOne() {
        assertEquals(-1, selectedFeedChipIndex(feedChips, pinnedLists, null))
    }

    @Test
    fun unknownUri_returnsMinusOne() {
        assertEquals(-1, selectedFeedChipIndex(feedChips, pinnedLists, "at://not/present"))
    }

    private fun chip(
        uri: String,
        kind: FeedKind,
        name: String,
    ): PinnedFeedUi = PinnedFeedUi(id = uri, uri = uri, kind = kind, displayName = name, avatarUrl = null)
}
