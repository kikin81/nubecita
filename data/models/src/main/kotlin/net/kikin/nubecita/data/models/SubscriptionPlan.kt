package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * One purchasable plan inside a [SubscriptionOffering] (e.g. the monthly or
 * the annual Nubecita Pro base plan).
 *
 * [formattedPrice] is the store-localized, currency-symbol-bearing string
 * the provider returns (e.g. `"$1.99"`, `"€2,29"`); render it verbatim — it
 * is already in the buyer's locale and currency, per Play policy. The raw
 * [priceAmountMicros] + [priceCurrencyCode] are retained for arithmetic the
 * formatted string can't support (per-month-equivalent and savings math in
 * [SubscriptionOffering]); one currency unit == 1_000_000 micros.
 */
@Immutable
public data class SubscriptionPlan(
    val id: SubscriptionPlanId,
    val period: BillingPeriod,
    val formattedPrice: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String,
)
