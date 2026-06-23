package net.kikin.nubecita.core.review

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class ReviewPolicyTest {
    private val now = Instant.parse("2026-06-23T12:00:00Z")

    /** A state that sits exactly on every gate's eligible boundary. */
    private fun eligibleState() =
        ReviewState(
            firstLaunchAt = now - 3.days,
            successfulPostCount = 3,
            requestCount = 0,
            lastRequestedAt = null,
        )

    @Test
    fun `eligible when all gates satisfied at their boundaries`() {
        assertTrue(ReviewPolicy.isEligible(eligibleState(), now))
    }

    @Test
    fun `not eligible below the post threshold`() {
        assertFalse(ReviewPolicy.isEligible(eligibleState().copy(successfulPostCount = 2), now))
    }

    @Test
    fun `not eligible within the new-user window`() {
        // Just under three days since first launch.
        val state = eligibleState().copy(firstLaunchAt = now - 3.days + 1.seconds)
        assertFalse(ReviewPolicy.isEligible(state, now))
    }

    @Test
    fun `not eligible when first launch was never stamped`() {
        assertFalse(ReviewPolicy.isEligible(eligibleState().copy(firstLaunchAt = null), now))
    }

    @Test
    fun `not eligible when the lifetime cap is reached`() {
        assertFalse(ReviewPolicy.isEligible(eligibleState().copy(requestCount = 3), now))
    }

    @Test
    fun `not eligible within the cooldown window`() {
        // Last request 89 days ago — inside the 90-day cooldown.
        val state = eligibleState().copy(requestCount = 1, lastRequestedAt = now - 89.days)
        assertFalse(ReviewPolicy.isEligible(state, now))
    }

    @Test
    fun `eligible once the cooldown has fully elapsed`() {
        val state = eligibleState().copy(requestCount = 1, lastRequestedAt = now - 90.days)
        assertTrue(ReviewPolicy.isEligible(state, now))
    }
}
