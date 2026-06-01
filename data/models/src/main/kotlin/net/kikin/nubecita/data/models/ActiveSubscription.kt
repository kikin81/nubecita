package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * The Pro subscription the signed-in user currently owns, as resolved from the
 * billing provider's active entitlement. Distinct from [SubscriptionPlan]
 * (a *purchasable* plan inside a [SubscriptionOffering]): this describes what
 * the user *already has*, surfaced by Settings' "Nubecita Pro" section to label
 * the current plan and build the manage-subscription deep link.
 *
 * Both fields are nullable on purpose — they degrade independently:
 * - [planId] is null when the provider's base-plan identifier doesn't map onto
 *   one of our known plans (a future/renamed base plan); Settings then shows a
 *   neutral "Active" label instead of "Annual" / "Monthly".
 * - [productId] is the store product identifier used as the `sku` in the Play
 *   manage-subscription deep link; null falls back to the package-level Play
 *   subscriptions page.
 *
 * SDK-agnostic — no RevenueCat / Play Billing type leaks into `:data:models`;
 * the billing `impl` maps the provider's entitlement onto this.
 */
@Immutable
public data class ActiveSubscription(
    val planId: SubscriptionPlanId?,
    val productId: String?,
)
