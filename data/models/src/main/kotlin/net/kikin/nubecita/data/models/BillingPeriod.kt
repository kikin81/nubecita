package net.kikin.nubecita.data.models

/**
 * The recurrence interval of a [SubscriptionPlan]. v1 offers exactly two,
 * mirroring the two Google Play base plans created for Nubecita Pro
 * (`monthly` / `annual`).
 *
 * Distinct from [SubscriptionPlanId] (which identifies *which* plan to
 * select/purchase): [BillingPeriod] describes *how often* a plan renews and
 * is the unit the offering's per-month-equivalent and savings math is
 * computed against. Kept SDK-agnostic — no RevenueCat / Play Billing period
 * type leaks into `:data:models`.
 */
public enum class BillingPeriod {
    Monthly,
    Annual,
}
