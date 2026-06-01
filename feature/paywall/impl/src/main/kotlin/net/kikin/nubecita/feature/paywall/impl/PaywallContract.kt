package net.kikin.nubecita.feature.paywall.impl

import android.app.Activity
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.SubscriptionOffering
import net.kikin.nubecita.data.models.SubscriptionPlanId

/**
 * MVI state for the Nubecita Pro paywall.
 *
 * [status] is a **sealed sum** ([PaywallStatus]) because the offering's
 * load lifecycle is mutually exclusive — at any moment the screen is
 * exactly one of loading the offering, showing it, or showing a load
 * error. The store-localized [SubscriptionOffering] rides inside
 * [PaywallStatus.Ready] because it only exists once the load succeeds.
 *
 * [selectedPlan] is **flat** and independent of [status]: it's a pure
 * UI selection that defaults to [SubscriptionPlanId.Annual] (design D9 —
 * the annual plan is the better-value default a supporter-framed paywall
 * leads with). It persists across a load retry and is meaningful only
 * once [status] is [PaywallStatus.Ready], where the plan picker reads it.
 *
 * [isPurchasing] / [isRestoring] are **flat** booleans because each can
 * legitimately coexist with a [PaywallStatus.Ready] body (the CTA shows
 * an inline spinner while the Play sheet is up; the Restore action shows
 * its own progress) and with each other being false — they are not a
 * mutually-exclusive mode of [status].
 */
data class PaywallState(
    val status: PaywallStatus = PaywallStatus.Loading,
    val selectedPlan: SubscriptionPlanId = SubscriptionPlanId.Annual,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
) : UiState

/**
 * The paywall's offering-load lifecycle. Per-screen sealed sum (not a
 * generic `Async<T>` wrapper, per CLAUDE.md MVI conventions) carrying
 * the loaded [SubscriptionOffering] as a per-variant payload.
 */
sealed interface PaywallStatus {
    /** Fetching the offering from the billing provider. */
    data object Loading : PaywallStatus

    /** Offering loaded; the plan picker + CTA render from [offering]. */
    data class Ready(
        val offering: SubscriptionOffering,
    ) : PaywallStatus

    /**
     * The offering load failed (no network / provider unavailable / a
     * keyless local or bench build where `loadPlans()` returns
     * `Result.failure`). The screen shows a retryable error rather than
     * an empty plan picker.
     */
    data object Error : PaywallStatus
}

/**
 * Events the paywall screen sends to the ViewModel.
 */
sealed interface PaywallEvent : UiEvent {
    /** User tapped "Try again" on the load-error state. Re-runs the offering load. */
    data object Retry : PaywallEvent

    /** User tapped a plan card in the picker. Updates [PaywallState.selectedPlan]. */
    data class PlanSelected(
        val planId: SubscriptionPlanId,
    ) : PaywallEvent

    /**
     * User tapped "Become a Supporter". Carries the hosting [activity]
     * because the Play purchase sheet attaches to a concrete Activity and
     * the VM has no Activity handle (design D5) — the screen supplies it
     * from `LocalActivity.current` at tap time. The VM uses it transiently
     * inside the purchase coroutine and never stores it.
     */
    data class PurchaseClicked(
        val activity: Activity,
    ) : PaywallEvent

    /** User tapped "Restore purchases". Re-syncs entitlements owned by the Play account. */
    data object RestoreClicked : PaywallEvent

    /** User tapped the "Terms of Service" disclosure link. */
    data object TermsClicked : PaywallEvent

    /** User tapped the "Privacy Policy" disclosure link. */
    data object PrivacyClicked : PaywallEvent
}

/**
 * One-shot effects collected by the screen.
 */
sealed interface PaywallEffect : UiEffect {
    /**
     * Pop the paywall off the back stack. Emitted on a **restore** that finds
     * Pro — the user already had the entitlement, so there's nothing to
     * celebrate or sell; just leave. (A fresh purchase emits [PurchaseSucceeded].)
     */
    data object Dismiss : PaywallEffect

    /**
     * A **fresh** purchase completed. The screen replaces the paywall with the
     * `PaywallSuccessRoute` thank-you screen (nubecita-ykpc) — a deliberately
     * separate effect from [Dismiss] so only a real purchase (never restore)
     * triggers the celebration.
     */
    data object PurchaseSucceeded : PaywallEffect

    /**
     * A purchase attempt failed (NOT a user cancel — that's the silent
     * happy-path exit and emits nothing). Copy is resolved at render time
     * from a localized string; [PurchaseResult.Error.message] is a
     * developer-facing diagnostic and is never surfaced verbatim.
     */
    data object ShowPurchaseError : PaywallEffect

    /** A restore attempt failed (the sync call itself errored). Localized at render time. */
    data object ShowRestoreError : PaywallEffect

    /** A restore completed but the Play account owns no Pro. Localized at render time. */
    data object ShowNothingToRestore : PaywallEffect

    /**
     * Open an external URL (Terms / Privacy) via the system's preferred
     * handler (Chrome Custom Tab when available). Mirrors
     * `:feature:settings`'s `LaunchUri`.
     */
    data class LaunchUri(
        val uri: String,
    ) : PaywallEffect
}
