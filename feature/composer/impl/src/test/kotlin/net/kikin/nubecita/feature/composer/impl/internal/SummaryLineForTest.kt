package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins the composer summary line's join rule. The language label itself comes
 * from [chipLabelFor] (pinned by `ChipLabelForTest`, incl. the "+N" overflow).
 */
internal class SummaryLineForTest {
    @Test
    fun `top-level post shows language and audience`() {
        assertEquals("English · Visible to all", summaryLineFor("English", "Visible to all"))
    }

    @Test
    fun `reply shows the language alone`() {
        assertEquals("English", summaryLineFor("English", null))
    }

    @Test
    fun `language overflow label passes through`() {
        assertEquals("English +2 · Interaction limited", summaryLineFor("English +2", "Interaction limited"))
    }
}
