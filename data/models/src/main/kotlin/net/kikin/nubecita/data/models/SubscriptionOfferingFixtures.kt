package net.kikin.nubecita.data.models

/**
 * Public preview / test fixtures for [SubscriptionOffering]. Lives in
 * `src/main` (as [NotificationItemUiFixtures] does) because the paywall
 * fans out across `:designsystem` previews, `:feature:paywall:impl`
 * previews + unit tests, and the screenshot-test source set — and Gradle's
 * `java-test-fixtures` isn't applied here. Public-in-main is the simplest
 * "usable from any module that depends on `:data:models`" surface.
 *
 * Production code SHOULD NOT call these factories (live prices come from the
 * provider's `Offerings`); they're tagged `Fixture` for grep-ability and
 * tree-shaking. R8 strips unused factories from release builds.
 *
 * The default prices are the D9 anchors ($1.99/mo, $19.99/yr ⇒ 16% off);
 * every parameter has a default so a caller can vary one dimension under
 * test (e.g. a different currency, or a non-discounted annual price).
 */
public object SubscriptionOfferingFixtures {
    public fun proOffering(
        monthlyFormattedPrice: String = "$1.99",
        monthlyPriceAmountMicros: Long = 1_990_000,
        annualFormattedPrice: String = "$19.99",
        annualPriceAmountMicros: Long = 19_990_000,
        currencyCode: String = "USD",
    ): SubscriptionOffering =
        SubscriptionOffering(
            monthly =
                SubscriptionPlan(
                    id = SubscriptionPlanId.Monthly,
                    period = BillingPeriod.Monthly,
                    formattedPrice = monthlyFormattedPrice,
                    priceAmountMicros = monthlyPriceAmountMicros,
                    priceCurrencyCode = currencyCode,
                ),
            annual =
                SubscriptionPlan(
                    id = SubscriptionPlanId.Annual,
                    period = BillingPeriod.Annual,
                    formattedPrice = annualFormattedPrice,
                    priceAmountMicros = annualPriceAmountMicros,
                    priceCurrencyCode = currencyCode,
                ),
        )
}
