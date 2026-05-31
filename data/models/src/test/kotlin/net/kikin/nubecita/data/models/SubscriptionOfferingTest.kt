package net.kikin.nubecita.data.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SubscriptionOfferingTest {
    private fun plan(
        id: SubscriptionPlanId,
        period: BillingPeriod,
        formattedPrice: String,
        priceAmountMicros: Long,
    ) = SubscriptionPlan(
        id = id,
        period = period,
        formattedPrice = formattedPrice,
        priceAmountMicros = priceAmountMicros,
        priceCurrencyCode = "USD",
    )

    // $1.99/mo and $19.99/yr are the D9 anchor prices. $19.99 / 12 = $1.665…,
    // i.e. 1_665_833 micros after integer truncation.
    private fun anchorOffering() =
        SubscriptionOffering(
            monthly = plan(SubscriptionPlanId.Monthly, BillingPeriod.Monthly, "$1.99", 1_990_000),
            annual = plan(SubscriptionPlanId.Annual, BillingPeriod.Annual, "$19.99", 19_990_000),
        )

    @Test
    fun `annualMonthlyEquivalentMicros divides the annual price by twelve`() {
        assertEquals(1_665_833L, anchorOffering().annualMonthlyEquivalentMicros)
    }

    @Test
    fun `annualSavingsPercent is sixteen for the anchor prices`() {
        // 12 * $1.99 = $23.88 paid monthly vs $19.99 annual ⇒ 16.3% saved,
        // truncated to a whole 16%. This is the "16% off" headline from D9.
        assertEquals(16, anchorOffering().annualSavingsPercent)
    }

    @Test
    fun `annualSavingsPercent truncates toward zero rather than rounding up`() {
        // 12 * $1.00 = $12.00 vs $10.50 annual ⇒ 12.5% saved ⇒ truncates to 12,
        // proving integer division floors (not rounds to 13).
        val offering =
            SubscriptionOffering(
                monthly = plan(SubscriptionPlanId.Monthly, BillingPeriod.Monthly, "$1.00", 1_000_000),
                annual = plan(SubscriptionPlanId.Annual, BillingPeriod.Annual, "$10.50", 10_500_000),
            )
        assertEquals(12, offering.annualSavingsPercent)
    }

    @Test
    fun `annualSavingsPercent is zero when the annual plan is not actually cheaper`() {
        // A misconfigured / promo price where the annual plan costs MORE than
        // twelve monthly charges must never surface a negative "save N%" badge.
        // monthly $1.99 (12x = 23_880_000) vs annual $29.99 (29_990_000) ⇒ raw
        // -25%, clamped to 0.
        val offering =
            SubscriptionOffering(
                monthly = plan(SubscriptionPlanId.Monthly, BillingPeriod.Monthly, "$1.99", 1_990_000),
                annual = plan(SubscriptionPlanId.Annual, BillingPeriod.Annual, "$29.99", 29_990_000),
            )
        assertEquals(0, offering.annualSavingsPercent)
    }

    @Test
    fun `annualSavingsPercent is zero when the monthly price is free`() {
        // Guards the savings divisor: a $0 monthly plan must not divide by zero.
        val offering =
            SubscriptionOffering(
                monthly = plan(SubscriptionPlanId.Monthly, BillingPeriod.Monthly, "$0.00", 0),
                annual = plan(SubscriptionPlanId.Annual, BillingPeriod.Annual, "$19.99", 19_990_000),
            )
        assertEquals(0, offering.annualSavingsPercent)
    }
}
