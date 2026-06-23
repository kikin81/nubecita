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
}
