package net.kikin.nubecita.core.billing

import kotlinx.coroutines.flow.StateFlow
import net.kikin.nubecita.data.models.ActiveSubscription

/**
 * The signed-in device's Nubecita Pro entitlement, decoupled from any billing
 * provider. The RevenueCat implementation is `internal` to this module; a fake
 * in-memory implementation backs downstream builds and tests until it lands.
 * No provider (`CustomerInfo`, `EntitlementInfo`, …) type is exposed, so the
 * provider is swappable by rewriting one module (design D1).
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
     * The active Pro subscription's identity (plan + store product id), or
     * `null` when Pro is inactive or not yet resolved. Tracks [isPro] — it is
     * non-null exactly when [isPro] is `true`. Settings reads it to label the
     * current plan and to build the manage-subscription deep link; features
     * that only need the on/off gate (PiP, the Supporter badge) stay on
     * [isPro]. A hot [StateFlow] for the same no-polling reason as [isPro].
     */
    public val activeSubscription: StateFlow<ActiveSubscription?>

    /**
     * Force a re-sync of entitlement state from the provider (e.g. after a
     * restore or when returning to a Pro-gated surface). Idempotent; the
     * result is delivered through [isPro], not returned, so there is a single
     * source of truth.
     */
    public suspend fun refresh()
}
