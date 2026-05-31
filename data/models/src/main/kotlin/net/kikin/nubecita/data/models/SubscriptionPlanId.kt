package net.kikin.nubecita.data.models

/**
 * Stable identifier the app uses to select a specific [SubscriptionPlan]
 * within a [SubscriptionOffering], independent of the provider's package /
 * SKU strings. The billing `impl` maps each provider package onto one of
 * these (and vice-versa when initiating a purchase), so feature code can
 * say "buy the annual plan" without ever naming a RevenueCat package id.
 */
public enum class SubscriptionPlanId {
    Monthly,
    Annual,
}
