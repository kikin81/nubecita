package net.kikin.nubecita.feature.search.impl.pane

import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class FeedVisibilityTest {
    private val feeds =
        listOf("a", "b", "c", "d", "e").map {
            DiscoverFeedUi(
                feed =
                    SuggestedFeedUi(
                        uri = it,
                        displayName = it,
                        creatorHandle = "h",
                        avatarUrl = null,
                        description = null,
                        isPinned = false,
                    ),
            )
        }

    @Test
    fun `one column reports only the first feed of each visible row`() {
        assertEquals(listOf("b", "c"), feedUrisInVisibleRows(listOf("b", "c"), 1, feeds))
    }

    @Test
    fun `two columns report both feeds of each visible row`() {
        assertEquals(listOf("a", "b", "c", "d"), feedUrisInVisibleRows(listOf("a", "c"), 2, feeds))
    }

    @Test
    fun `a last row with a single cell reports one feed`() {
        assertEquals(listOf("e"), feedUrisInVisibleRows(listOf("e"), 2, feeds))
    }

    @Test
    fun `an unknown row key reports nothing`() {
        assertEquals(emptyList<String>(), feedUrisInVisibleRows(listOf("zzz"), 2, feeds))
    }
}
