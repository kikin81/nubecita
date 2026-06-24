package net.kikin.nubecita.core.update

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UpdatePolicyTest {
    private fun signals(
        availability: Int = UpdateAvailability.UPDATE_AVAILABLE,
        flexible: Boolean = true,
        immediate: Boolean = true,
        priority: Int = 0,
        staleness: Int? = null,
        versionCode: Int = 100,
    ) = UpdateSignals(availability, flexible, immediate, priority, staleness, versionCode)

    @Test
    fun `no update available is None`() {
        assertEquals(UpdateAction.None, UpdatePolicy.decide(signals(availability = UpdateAvailability.UPDATE_NOT_AVAILABLE), null))
    }

    @Test
    fun `available, low priority, not yet prompted is Flexible`() {
        assertEquals(UpdateAction.Flexible, UpdatePolicy.decide(signals(priority = 1), lastPromptedVersionCode = null))
    }

    @Test
    fun `same versionCode already prompted is throttled to None`() {
        assertEquals(UpdateAction.None, UpdatePolicy.decide(signals(versionCode = 100), lastPromptedVersionCode = 100))
    }

    @Test
    fun `new higher versionCode re-arms Flexible`() {
        assertEquals(UpdateAction.Flexible, UpdatePolicy.decide(signals(versionCode = 101), lastPromptedVersionCode = 100))
    }

    @Test
    fun `priority at threshold is Immediate ignoring throttle`() {
        assertEquals(UpdateAction.Immediate, UpdatePolicy.decide(signals(priority = 4, versionCode = 100), lastPromptedVersionCode = 100))
    }

    @Test
    fun `high staleness is Immediate`() {
        assertEquals(UpdateAction.Immediate, UpdatePolicy.decide(signals(priority = 0, staleness = 60), null))
    }

    @Test
    fun `staleness just below threshold is not Immediate`() {
        assertEquals(
            UpdateAction.Flexible,
            UpdatePolicy.decide(signals(priority = 0, staleness = 59), lastPromptedVersionCode = null),
        )
    }

    @Test
    fun `graceful fallback - high priority but immediate not allowed is Flexible`() {
        // Intentional: a priority-5 update with isImmediateAllowed=false degrades to the gentle flow, not None.
        assertEquals(
            UpdateAction.Flexible,
            UpdatePolicy.decide(signals(priority = 5, immediate = false, versionCode = 200), lastPromptedVersionCode = null),
        )
    }

    @Test
    fun `flexible not allowed and not immediate is None`() {
        assertEquals(UpdateAction.None, UpdatePolicy.decide(signals(flexible = false, immediate = false), null))
    }
}
