package net.kikin.nubecita.core.billing

import android.app.Activity
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
 */
@Singleton
internal class RevenueCatBillingRepository
    @Inject
    constructor(
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : BillingRepository {
        override suspend fun loadPlans(): Result<SubscriptionOffering> =
            withContext(ioDispatcher) {
                Purchases.sharedInstance
                    .awaitOfferingsResult()
                    .fold(
                        onSuccess = { it.toSubscriptionOfferingResult() },
                        onFailure = { Result.failure(it) },
                    )
            }

        override suspend fun purchase(
            activity: Activity,
            plan: SubscriptionPlan,
        ): PurchaseResult {
            // Resolve the SubscriptionPlan back to the live RevenueCat Package the
            // purchase API requires. Re-reading offerings keeps purchase() honest
            // about current availability rather than trusting a stale plan.
            val offerings =
                Purchases.sharedInstance
                    .awaitOfferingsResult()
                    .getOrElse { return PurchaseResult.Error(it.message ?: "Could not load plans") }
            val current = offerings.current ?: return PurchaseResult.Error("No current offering")
            val rcPackage =
                when (plan.id) {
                    SubscriptionPlanId.Monthly -> current.monthly
                    SubscriptionPlanId.Annual -> current.annual
                } ?: return PurchaseResult.Error("Selected plan is unavailable")

            return try {
                Purchases.sharedInstance.awaitPurchase(PurchaseParams.Builder(activity, rcPackage).build())
                PurchaseResult.Success
            } catch (e: PurchasesTransactionException) {
                e.toPurchaseResult()
            }
        }

        override suspend fun restorePurchases(): RestoreResult =
            withContext(ioDispatcher) {
                Purchases.sharedInstance
                    .awaitRestoreResult()
                    .fold(
                        onSuccess = { RestoreResult.Completed(isPro = it.hasProEntitlement()) },
                        onFailure = { RestoreResult.Error(it.message ?: "Restore failed") },
                    )
            }
    }
