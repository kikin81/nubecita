package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubscriptionOfferingFixturesTest {
    @Test
    fun `proOffering carries the D9 anchor prices`() {
        val offering = SubscriptionOfferingFixtures.proOffering()

        assertEquals(SubscriptionPlanId.Monthly, offering.monthly.id)
        assertEquals(BillingPeriod.Monthly, offering.monthly.period)
        assertEquals("$1.99", offering.monthly.formattedPrice)

        assertEquals(SubscriptionPlanId.Annual, offering.annual.id)
        assertEquals(BillingPeriod.Annual, offering.annual.period)
        assertEquals("$19.99", offering.annual.formattedPrice)
    }

    @Test
    fun `proOffering yields the sixteen percent savings headline`() {
        // Pins the fixture to the design's "16% off" annual headline so a
        // future price tweak that breaks the story fails loudly here.
        assertEquals(16, SubscriptionOfferingFixtures.proOffering().annualSavingsPercent)
    }
}
