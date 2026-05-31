package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * The set of plans presented together on the paywall — Nubecita Pro's single
 * v1 offering with a [monthly] and an [annual] base plan that both unlock the
 * same `pro` entitlement.
 *
 * Both plans MUST share a currency ([SubscriptionPlan.priceCurrencyCode]) for
 * the derived comparisons to be meaningful; the provider returns store-
 * localized prices in the buyer's single currency, so this holds in practice.
 */
@Immutable
public data class SubscriptionOffering(
    val monthly: SubscriptionPlan,
    val annual: SubscriptionPlan,
) {
    /**
     * The annual plan's price expressed as a per-month figure (micros),
     * for the "$X.XX/mo, billed annually" comparison line. Integer division
     * truncates the sub-micro remainder — negligible at currency-micro scale.
     */
    val annualMonthlyEquivalentMicros: Long
        get() = annual.priceAmountMicros / MONTHS_PER_YEAR

    /**
     * Whole-percent saved by paying annually instead of twelve monthly
     * charges — the paywall's "save N%" badge. Truncated toward zero so the
     * headline never overstates the discount, and floored at 0 so a
     * misconfigured/promo price where the annual plan is not actually cheaper
     * suppresses the badge rather than advertising a negative discount.
     * Returns 0 when the monthly price is free (guards the divisor).
     */
    val annualSavingsPercent: Int
        get() {
            val yearAtMonthlyRate = monthly.priceAmountMicros * MONTHS_PER_YEAR
            if (yearAtMonthlyRate <= 0L) return 0
            val saved = yearAtMonthlyRate - annual.priceAmountMicros
            return ((saved * 100) / yearAtMonthlyRate).toInt().coerceAtLeast(0)
        }

    private companion object {
        const val MONTHS_PER_YEAR = 12L
    }
}
