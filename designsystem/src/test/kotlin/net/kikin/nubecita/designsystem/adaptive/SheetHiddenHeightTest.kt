package net.kikin.nubecita.designsystem.adaptive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The hidden height decides how far the docked comment pill is pushed back up,
 * so every answer here is a position on screen. A wrong one puts the pill
 * off-screen, which is the bug this logic exists to fix (nubecita-970o).
 */
internal class SheetHiddenHeightTest {
    @Test
    fun `an offset the sheet has not produced yet hides nothing`() {
        // `requireOffset` throws before the first layout pass; the provider
        // turns that into null, and nothing should move.
        assertEquals(0, hiddenSheetHeightPx(null))
    }

    @Test
    fun `a non-finite offset hides nothing`() {
        assertEquals(0, hiddenSheetHeightPx(Float.NaN))
        assertEquals(0, hiddenSheetHeightPx(Float.POSITIVE_INFINITY))
        assertEquals(0, hiddenSheetHeightPx(Float.NEGATIVE_INFINITY))
    }

    @Test
    fun `an over-dragged sheet hides nothing rather than shoving content down`() {
        // Dragging past the top anchor gives a negative offset. Using it as-is
        // would offset the pill the wrong way, down and off the bottom.
        assertEquals(0, hiddenSheetHeightPx(-120f))
    }

    @Test
    fun `a fully expanded sheet hides nothing`() {
        assertEquals(0, hiddenSheetHeightPx(0f))
    }

    @Test
    fun `a partially expanded sheet hides everything below the window`() {
        // The value measured on device when the bug was reproduced.
        assertEquals(1832, hiddenSheetHeightPx(1832.0096f))
    }

    @Test
    fun `the offset rounds rather than truncates`() {
        assertEquals(1833, hiddenSheetHeightPx(1832.6f))
        assertEquals(1832, hiddenSheetHeightPx(1832.4f))
    }
}
