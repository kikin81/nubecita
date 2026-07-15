package net.kikin.nubecita.feature.postdetail.impl.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [shouldShowAuthorInBar], the pure scroll-decision function
 * behind the post-detail scroll-reactive toolbar. Every branch is exercised
 * here on the JVM — no device, no Compose runtime — which is where the
 * disambiguation logic (including the "focus off the top vs. below the fold"
 * case that the bench flavor can't reach) actually gets its coverage.
 *
 * Thresholds mirror the production dp constants at an assumed density: enter
 * 56px, exit 40px. The band between them (40–56) is the hysteresis zone.
 */
internal class PostDetailTopBarTest {
    private val enter = 56
    private val exit = 40

    @Test
    fun `no focus row -- always hidden`() {
        // focusIndex == -1 short-circuits regardless of every other argument.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = -1,
                firstVisibleItemIndex = 5,
                focusItemTopPx = -1000,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = true,
            ),
        )
    }

    @Test
    fun `focus absent and scrolled off the top -- shown`() {
        // focusItemTopPx null (not in visibleItemsInfo) AND focusIndex is behind
        // the first visible item => it scrolled off the top => show the author.
        assertTrue(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 3,
                focusItemTopPx = null,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `focus absent and still below the fold -- hidden`() {
        // focusItemTopPx null AND focusIndex >= first visible => not reached yet.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = 4,
                firstVisibleItemIndex = 1,
                focusItemTopPx = null,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `focus absent off the top stays shown even if index equals first visible boundary`() {
        // Guard the strict "<" in the predicate: focusIndex == firstVisibleItemIndex
        // with a null offset means the row is not measured but also not behind the
        // first visible one => treat as below the fold => hidden.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = 2,
                firstVisibleItemIndex = 2,
                focusItemTopPx = null,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `focus visible below the bar -- hidden`() {
        // Positive offset => the focus card's top is still below the bar bottom.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = 784,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `focus visible tucked just under the enter threshold -- hidden`() {
        // tucked = 55, below the 56 enter threshold, not currently shown.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = -55,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `focus visible tucked at the enter threshold -- shown`() {
        // tucked = 56, meets the >= enter threshold.
        assertTrue(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = -56,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `hysteresis band -- stays shown when already shown`() {
        // tucked = 48, inside the 40..56 band. currentlyShown=true stays true.
        assertTrue(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = -48,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = true,
            ),
        )
    }

    @Test
    fun `hysteresis band -- stays hidden when already hidden`() {
        // tucked = 48, inside the 40..56 band. currentlyShown=false stays false.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = -48,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = false,
            ),
        )
    }

    @Test
    fun `shown collapses once it comes back out past the exit threshold`() {
        // tucked = 40, NOT > exit(40), so a currently-shown bar hides again.
        assertFalse(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = -40,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = true,
            ),
        )
    }

    @Test
    fun `shown holds while still tucked past the exit threshold`() {
        // tucked = 41, just > exit(40), currently shown => stays shown.
        assertTrue(
            shouldShowAuthorInBar(
                focusIndex = 0,
                firstVisibleItemIndex = 0,
                focusItemTopPx = -41,
                enterThresholdPx = enter,
                exitThresholdPx = exit,
                currentlyShown = true,
            ),
        )
    }
}
