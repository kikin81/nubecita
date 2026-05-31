package net.kikin.nubecita.core.billing.api

import android.app.Activity
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlan

/**
 * Provider-agnostic entry point for purchasing and restoring Nubecita Pro.
 * Maps the provider's offerings onto our [SubscriptionOffering] and translates
 * purchase/restore outcomes into [PurchaseResult] / [RestoreResult]; no
 * RevenueCat / Play Billing type ever crosses this boundary (design D1).
 *
 * Entitlement *state* is read from [EntitlementRepository.isPro]; this
 * repository only initiates transactions. A successful [purchase] or a
 * [restorePurchases] that finds Pro will drive that flow to `true`.
 */
public interface BillingRepository {
    /**
     * Fetch the current Pro offering with store-localized prices. Returns a
     * [Result] so the paywall can show a retryable error state when the
     * provider/network is unavailable, rather than an empty plan picker.
     */
    public suspend fun loadPlans(): Result<SubscriptionOffering>

    /**
     * Launch the provider's purchase flow for [plan]. Requires the hosting
     * [activity] because the Play purchase sheet attaches to a concrete
     * Activity — the call site is the Composable layer (design D5), never a
     * ViewModel, which has no Activity handle.
     */
    public suspend fun purchase(
        activity: Activity,
        plan: SubscriptionPlan,
    ): PurchaseResult

    /** Re-sync purchases owned by the current Play account (the paywall/settings "Restore" action). */
    public suspend fun restorePurchases(): RestoreResult
}
