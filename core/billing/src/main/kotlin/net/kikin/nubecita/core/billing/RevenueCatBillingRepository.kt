package net.kikin.nubecita.core.billing

import android.app.Activity
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitOfferingsResult
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestoreResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlan
import net.kikin.nubecita.data.models.SubscriptionPlanId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RevenueCat-backed [BillingRepository]. Translates `Offerings` to our
 * [SubscriptionOffering] (via [toSubscriptionOfferingResult]) and drives the
 * Play purchase / restore flows. Entitlement *state* is observed separately
 * through [RevenueCatEntitlementRepository]; a completed [purchase] or a
 * [restorePurchases] that finds Pro reaches `isPro` via that listener.
 *
 * Every entry point first checks [Purchases.isConfigured]: on the bench flavor
 * (no configure) or a keyless build (`RevenueCatInitializer` skips configure),
 * `Purchases.sharedInstance` would otherwise throw
 * `UninitializedPropertyAccessException` synchronously — outside the SDK's
 * `Result`/exception model — and crash the caller. Guarding keeps the boundary's
 * "inert, not crashing" promise and lets the paywall show its error state.
 */
@Singleton
internal class RevenueCatBillingRepository
    @Inject
    constructor(
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BillingRepository {
        override suspend fun loadPlans(): Result<SubscriptionOffering> {
            if (!Purchases.isConfigured) return Result.failure(IllegalStateException(BILLING_UNAVAILABLE))
            return withContext(ioDispatcher) {
                Purchases.sharedInstance
                    .awaitOfferingsResult()
                    .fold(
                        onSuccess = { it.toSubscriptionOfferingResult() },
                        onFailure = { Result.failure(it) },
                    )
            }
        }

        override suspend fun purchase(
            activity: Activity,
            plan: SubscriptionPlan,
        ): PurchaseResult {
            if (!Purchases.isConfigured) return PurchaseResult.Error(BILLING_UNAVAILABLE)
            // Resolve the SubscriptionPlan back to the live RevenueCat Package the
            // purchase API requires (the :data:models plan can't carry a provider
            // type). Re-reading offerings keeps purchase() honest about current
            // availability rather than trusting a stale plan.
            val rcPackage =
                resolvePackage(plan.id) ?: return PurchaseResult.Error("Selected plan is unavailable")
            return try {
                Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, rcPackage).build())
                PurchaseResult.Success
            } catch (e: PurchasesTransactionException) {
                e.toPurchaseResult()
            }
        }

        override suspend fun restorePurchases(): RestoreResult {
            if (!Purchases.isConfigured) return RestoreResult.Error(BILLING_UNAVAILABLE)
            return withContext(ioDispatcher) {
                Purchases.sharedInstance
                    .awaitRestoreResult()
                    .fold(
                        onSuccess = { RestoreResult.Completed(isPro = it.hasProEntitlement()) },
                        onFailure = { RestoreResult.Error(it.message ?: "Restore failed") },
                    )
            }
        }

        /** Look up the live [Package] for [planId] in the current offering, off the main thread. */
        private suspend fun resolvePackage(planId: SubscriptionPlanId): Package? =
            withContext(ioDispatcher) {
                val current =
                    Purchases.sharedInstance
                        .awaitOfferingsResult()
                        .getOrNull()
                        ?.current ?: return@withContext null
                when (planId) {
                    SubscriptionPlanId.Monthly -> current.monthly
                    SubscriptionPlanId.Annual -> current.annual
                }
            }

        private companion object {
            const val BILLING_UNAVAILABLE = "Billing is not available"
        }
    }
