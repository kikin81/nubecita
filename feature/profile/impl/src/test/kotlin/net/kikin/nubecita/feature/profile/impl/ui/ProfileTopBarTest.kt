package net.kikin.nubecita.feature.profile.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [computeBarAlpha]. The function maps the LazyListState's
 * (firstVisibleItemIndex, firstVisibleItemScrollOffset) and the hero's
 * fade-window height into a 0..1 alpha applied to the bar's backdrop +
 * title.
 *
 * Threshold semantics:
 * - hero unscrolled (index=0, offset=0)            → alpha=0 (bar invisible)
 * - hero scrolled half-window                       → alpha=0.5
 * - hero scrolled full window                       → alpha=1 (clamp)
 * - hero scrolled past window (overshoot)           → alpha=1 (clamp)
 * - hero fully scrolled away (index>0)              → alpha=1
 * - hero hasn't measured (fadeWindowPx=0)           → alpha=0 (avoid divide-by-zero)
 */
internal class ProfileTopBarTest {
    @Test
    fun `unscrolled hero yields alpha 0`() {
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, fadeWindowPx = 100))
    }

    @Test
    fun `mid-window scroll yields linear-lerped alpha`() {
        assertEquals(0.5f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, fadeWindowPx = 100))
    }

    @Test
    fun `full-window scroll yields alpha 1`() {
        assertEquals(1f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 100, fadeWindowPx = 100))
    }

    @Test
    fun `overshoot beyond window clamps to alpha 1`() {
        assertEquals(1f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 250, fadeWindowPx = 100))
    }

    @Test
    fun `hero scrolled past first item yields alpha 1`() {
        assertEquals(1f, computeBarAlpha(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0, fadeWindowPx = 100))
    }

    @Test
    fun `unmeasured hero (fadeWindowPx 0) yields alpha 0`() {
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 0, fadeWindowPx = 0))
    }

    @Test
    fun `unmeasured hero with positive offset still yields alpha 0`() {
        // Defensive: if the hero hasn't measured but the LazyColumn somehow reports
        // a positive offset, we don't want a divide-by-zero or NaN to leak through.
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, fadeWindowPx = 0))
    }

    @Test
    fun `negative fadeWindowPx defensively yields alpha 0`() {
        // Shouldn't be reachable in practice (fadeMultiplier * size is always non-negative),
        // but guard against future changes that could send a negative value through.
        assertEquals(0f, computeBarAlpha(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = 50, fadeWindowPx = -10))
    }
}
