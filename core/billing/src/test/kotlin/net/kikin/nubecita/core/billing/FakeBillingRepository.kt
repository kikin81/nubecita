package net.kikin.nubecita.core.billing

import android.app.Activity
import net.kikin.nubecita.data.models.BillingPeriod
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlan
import net.kikin.nubecita.data.models.SubscriptionPlanId

/**
 * In-memory [BillingRepository] test double. Serves a fixed offering with the D9
 * anchor prices and simulates a successful purchase by flipping the supplied
 * [FakeEntitlementRepository]. Not part of the Hilt graph (production binds the
 * RevenueCat impl) — constructed directly by `:core:billing`'s unit tests.
 */
internal class FakeBillingRepository(
    private val entitlement: FakeEntitlementRepository,
) : BillingRepository {
    override suspend fun loadPlans(): Result<SubscriptionOffering> = Result.success(PRO_OFFERING)

    override suspend fun purchase(
        activity: Activity,
        plan: SubscriptionPlan,
    ): PurchaseResult {
        entitlement.setPro(true)
        return PurchaseResult.Success
    }

    override suspend fun restorePurchases(): RestoreResult = RestoreResult.Completed(isPro = entitlement.isPro.value)

    private companion object {
        val PRO_OFFERING =
            SubscriptionOffering(
                monthly =
                    SubscriptionPlan(
                        id = SubscriptionPlanId.Monthly,
                        period = BillingPeriod.Monthly,
                        formattedPrice = "$1.99",
                        priceAmountMicros = 1_990_000,
                        priceCurrencyCode = "USD",
                    ),
                annual =
                    SubscriptionPlan(
                        id = SubscriptionPlanId.Annual,
                        period = BillingPeriod.Annual,
                        formattedPrice = "$19.99",
                        priceAmountMicros = 19_990_000,
                        priceCurrencyCode = "USD",
                    ),
            )
    }
}
