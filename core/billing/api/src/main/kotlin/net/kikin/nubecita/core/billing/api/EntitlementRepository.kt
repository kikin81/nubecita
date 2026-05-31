package net.kikin.nubecita.core.billing.api

import kotlinx.coroutines.flow.StateFlow

/**
 * The signed-in device's Nubecita Pro entitlement, decoupled from any billing
 * provider. The RevenueCat implementation lives in `:core:billing:impl`; a
 * fake in-memory implementation backs downstream builds and tests until it
 * lands. No provider (`CustomerInfo`, `EntitlementInfo`, …) type crosses this
 * boundary, so the provider is swappable by rewriting one module (design D1).
 *
 * Pro is bound to the Google Play account (an anonymous provider `appUserId`),
 * not the Bluesky DID (design D3), so the entitlement survives a deleted
 * Bluesky account and follows the Play account across devices via Restore.
 */
public interface EntitlementRepository {
    /**
     * Whether the `pro` entitlement is currently active. A hot [StateFlow] so
     * features observe transitions (purchase, restore, expiry) without polling.
     * Starts `false` and is never `null` — features must never *synchronously*
     * gate on a one-shot check, only react to this stream (design risk note:
     * "entitlement check blocking UI / cold-start latency").
     */
    public val isPro: StateFlow<Boolean>

    /**
     * Force a re-sync of entitlement state from the provider (e.g. after a
     * restore or when returning to a Pro-gated surface). Idempotent; the
     * result is delivered through [isPro], not returned, so there is a single
     * source of truth.
     */
    public suspend fun refresh()
}
