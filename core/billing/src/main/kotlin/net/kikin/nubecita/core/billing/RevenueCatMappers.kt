package net.kikin.nubecita.core.billing

import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchasesTransactionException
import net.kikin.nubecita.data.models.ActiveSubscription
import net.kikin.nubecita.data.models.BillingPeriod
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlan
import net.kikin.nubecita.data.models.SubscriptionPlanId

/**
 * The RevenueCat entitlement identifier that grants Nubecita Pro. Must match
 * the entitlement configured in the RevenueCat dashboard (design D8 / task 11).
 */
internal const val PRO_ENTITLEMENT_ID = "pro"

/*
 * Pure RevenueCat-wire -> :data:models mappers. Kept as standalone `internal`
 * functions (not buried in the repositories) so the translation — the part with
 * real branching and the most ways to be wrong — is unit-testable by mocking
 * only the SDK value types, with no `Purchases.sharedInstance` static in play.
 */

/** True when the `pro` entitlement is in the active set. */
internal fun CustomerInfo.hasProEntitlement(): Boolean = PRO_ENTITLEMENT_ID in entitlements.active

/**
 * The active `pro` subscription's identity, or null when Pro isn't active.
 * Drives Settings' current-plan label + manage-subscription deep link.
 *
 * [ActiveSubscription.planId] maps the Play **base-plan** identifier
 * (`productPlanIdentifier`, e.g. `"monthly"` / `"annual"` — the ids configured
 * in task 11) onto our plan enum; an unrecognized base plan yields null so the
 * UI shows a neutral label rather than guessing. [ActiveSubscription.productId]
 * is the store product identifier used as the deep link `sku`.
 */
internal fun CustomerInfo.activeProSubscription(): ActiveSubscription? {
    val pro = entitlements.active[PRO_ENTITLEMENT_ID] ?: return null
    return ActiveSubscription(
        planId = pro.productPlanIdentifier?.toSubscriptionPlanId(),
        productId = pro.productIdentifier,
    )
}

/** Map a Play base-plan id onto our plan enum. Case-insensitive; unknown → null. */
private fun String.toSubscriptionPlanId(): SubscriptionPlanId? =
    when (lowercase()) {
        "monthly" -> SubscriptionPlanId.Monthly
        "annual" -> SubscriptionPlanId.Annual
        else -> null
    }

/**
 * Map the provider's current offering to our [SubscriptionOffering]. Fails (so
 * the paywall shows a retryable error rather than a half-populated picker) when
 * there is no current offering or it is missing the monthly/annual base plan —
 * both are dashboard-configuration problems the UI can't paper over.
 */
internal fun Offerings.toSubscriptionOfferingResult(): Result<SubscriptionOffering> =
    runCatching {
        val offering = requireNotNull(current) { "RevenueCat returned no current offering" }
        val monthly = requireNotNull(offering.monthly) { "Current offering has no monthly package" }
        val annual = requireNotNull(offering.annual) { "Current offering has no annual package" }
        SubscriptionOffering(
            monthly = monthly.toSubscriptionPlan(SubscriptionPlanId.Monthly, BillingPeriod.Monthly),
            annual = annual.toSubscriptionPlan(SubscriptionPlanId.Annual, BillingPeriod.Annual),
        )
    }

private fun Package.toSubscriptionPlan(
    id: SubscriptionPlanId,
    period: BillingPeriod,
): SubscriptionPlan {
    val price = product.price
    return SubscriptionPlan(
        id = id,
        period = period,
        formattedPrice = price.formatted,
        priceAmountMicros = price.amountMicros,
        priceCurrencyCode = price.currencyCode,
    )
}

/**
 * Translate a failed purchase into a [PurchaseResult]. A user backing out of the
 * Play sheet ([PurchasesTransactionException.userCancelled]) is the silent
 * happy-path exit ([PurchaseResult.Cancelled]); anything else is a real
 * [PurchaseResult.Error] carrying the SDK's message — a developer-facing
 * diagnostic (often English), not user-ready copy (see [PurchaseResult.Error]).
 */
internal fun PurchasesTransactionException.toPurchaseResult(): PurchaseResult =
    if (userCancelled) {
        PurchaseResult.Cancelled
    } else {
        PurchaseResult.Error(message ?: "Purchase failed")
    }
