package net.kikin.nubecita.core.review

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ReviewPolicyTest {
    private val now = Instant.parse("2026-06-23T12:00:00Z")
    private val installedThreeDaysAgo = now - 3.days

    /** Counters that sit on every gate's eligible boundary. */
    private fun eligibleState() =
        ReviewState(
            successfulPostCount = 3,
            requestCount = 0,
            lastRequestedAt = null,
        )

    private fun isEligible(
        state: ReviewState = eligibleState(),
        firstInstallTime: Instant = installedThreeDaysAgo,
    ) = ReviewPolicy.isEligible(state, firstInstallTime, now)

    @Test
    fun `eligible when all gates satisfied at their boundaries`() {
        assertTrue(isEligible())
    }

    @Test
    fun `not eligible below the post threshold`() {
        assertFalse(isEligible(state = eligibleState().copy(successfulPostCount = 2)))
    }

    @Test
    fun `not eligible within the new-user window`() {
        // Installed just under three days ago.
        assertFalse(isEligible(firstInstallTime = now - 3.days + 1.seconds))
    }

    @Test
    fun `not eligible when the lifetime cap is reached`() {
        assertFalse(isEligible(state = eligibleState().copy(requestCount = 3)))
    }

    @Test
    fun `not eligible within the cooldown window`() {
        assertFalse(isEligible(state = eligibleState().copy(requestCount = 1, lastRequestedAt = now - 89.days)))
    }

    @Test
    fun `eligible once the cooldown has fully elapsed`() {
        assertTrue(isEligible(state = eligibleState().copy(requestCount = 1, lastRequestedAt = now - 90.days)))
    }

    /** A reader who has never posted: the path that didn't exist before. */
    private fun readerState() =
        ReviewState(
            successfulPostCount = 0,
            requestCount = 0,
            lastRequestedAt = null,
            activeDayCount = 5,
            likeCount = 3,
        )

    @Test
    fun `a reader with enough active days and likes is eligible`() {
        assertTrue(isEligible(state = readerState()))
    }

    @Test
    fun `a reader below the active-day threshold is not eligible`() {
        assertFalse(isEligible(state = readerState().copy(activeDayCount = 4)))
    }

    @Test
    fun `a reader below the like threshold is not eligible`() {
        assertFalse(isEligible(state = readerState().copy(likeCount = 2)))
    }

    @Test
    fun `a reader still waits out the cooldown`() {
        assertFalse(isEligible(state = readerState().copy(requestCount = 1, lastRequestedAt = now - 89.days)))
    }

    @Test
    fun `a reader is still not asked in the new-user window`() {
        assertFalse(isEligible(state = readerState(), firstInstallTime = now - 3.days + 1.seconds))
    }
}
