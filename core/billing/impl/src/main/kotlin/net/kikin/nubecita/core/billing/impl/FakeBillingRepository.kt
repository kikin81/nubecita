package net.kikin.nubecita.core.billing.impl

import android.app.Activity
import net.kikin.nubecita.core.billing.api.BillingRepository
import net.kikin.nubecita.core.billing.api.PurchaseResult
import net.kikin.nubecita.core.billing.api.RestoreResult
import net.kikin.nubecita.data.models.BillingPeriod
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlan
import net.kikin.nubecita.data.models.SubscriptionPlanId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [BillingRepository] standing in for the RevenueCat implementation
 * (`nubecita-q5ge.2`). It serves a fixed offering with the D9 anchor prices and
 * simulates a successful purchase by flipping the shared [FakeEntitlementRepository],
 * so an end-to-end paywall → entitlement → Pro-gate flow can be exercised
 * before any real billing SDK exists.
 *
 * The offering is constructed inline rather than via
 * `SubscriptionOfferingFixtures` because that factory is a test/preview fixture
 * (R8-strippable, "not for production"); this fake is wired into real builds.
 */
@Singleton
internal class FakeBillingRepository
    @Inject
    constructor(
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
