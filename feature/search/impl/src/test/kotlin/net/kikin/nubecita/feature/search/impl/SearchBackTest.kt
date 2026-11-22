package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchBackTest {
    private val phases = listOf(SearchPhase.Discover, SearchPhase.Results("kotlin"))

    @Test
    fun `back clears the search only on results at the top of the stack with the bar collapsed`() {
        for (isTop in listOf(true, false)) {
            for (phase in phases) {
                for (collapsed in listOf(true, false)) {
                    val expected = isTop && phase is SearchPhase.Results && collapsed
                    assertEquals(
                        expected,
                        shouldClearSearchOnBack(isTopOfTabStack = isTop, phase = phase, isBarCollapsed = collapsed),
                        "isTop=$isTop phase=$phase collapsed=$collapsed",
                    )
                }
            }
        }
    }
}
