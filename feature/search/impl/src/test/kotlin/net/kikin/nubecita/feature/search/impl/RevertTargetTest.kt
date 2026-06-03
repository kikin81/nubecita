package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The search bar's back affordance reverts the field to [revertTargetFor]
 * when collapsing without a submit, so the collapsed pill text and the body
 * (driven by `SearchPhase`) stay consistent.
 */
class RevertTargetTest {
    @Test
    fun results_revertsToTheSubmittedQuery() {
        assertEquals("kotlin", revertTargetFor(SearchPhase.Results("kotlin")))
    }

    @Test
    fun discover_revertsToBlank() {
        assertEquals("", revertTargetFor(SearchPhase.Discover))
    }
}
