package net.kikin.nubecita.feature.paywall.impl

import android.app.Activity
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.billing.BillingRepository
import net.kikin.nubecita.core.billing.PurchaseResult
import net.kikin.nubecita.core.billing.RestoreResult
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.SubscriptionPlan
import net.kikin.nubecita.data.models.SubscriptionPlanId
import timber.log.Timber
import javax.inject.Inject

/**
 * Presenter for the Nubecita Pro paywall. Owns:
 *
 * - The offering load (init + [PaywallEvent.Retry]): [BillingRepository.loadPlans]
 *   drives [PaywallStatus] Loading → Ready / Error. On a keyless local or
 *   bench build the bound RevenueCat impl returns `Result.failure`, so the
 *   Error/retry path is the one exercised there.
 * - Plan selection ([PaywallEvent.PlanSelected]) — a flat UI selection
 *   defaulting to annual (design D9).
 * - The purchase flow ([PaywallEvent.PurchaseClicked]): the screen supplies
 *   the hosting Activity (design D5); the VM uses it transiently to call
 *   [BillingRepository.purchase] and never stores it. [PurchaseResult.Success]
 *   → [PaywallEffect.Dismiss]; [PurchaseResult.Cancelled] → silently stay;
 *   [PurchaseResult.Error] → [PaywallEffect.ShowPurchaseError] (the provider's
 *   developer-facing message is logged, never surfaced).
 * - Restore ([PaywallEvent.RestoreClicked]) and the Terms / Privacy link taps.
 *
 * Per CLAUDE.md MVI conventions: state is flat / per-screen-sealed (no
 * `Async<T>`), and remote work is an inline `viewModelScope.launch { ... }`
 * with the recovery shape visible at each call site rather than a base-class
 * helper. [purchase] and [restore] are single-flight, guarding on the
 * in-flight flag so a double tap can't launch two Play sheets / syncs.
 */
@HiltViewModel
internal class PaywallViewModel
    @Inject
    constructor(
        private val billingRepository: BillingRepository,
    ) : MviViewModel<PaywallState, PaywallEvent, PaywallEffect>(PaywallState()) {
        init {
            loadPlans()
        }

        override fun handleEvent(event: PaywallEvent) {
            when (event) {
                PaywallEvent.Retry -> loadPlans()
                is PaywallEvent.PlanSelected -> setState { copy(selectedPlan = event.planId) }
                is PaywallEvent.PurchaseClicked -> purchase(event.activity)
                PaywallEvent.RestoreClicked -> restore()
                PaywallEvent.TermsClicked -> sendEffect(PaywallEffect.LaunchUri(TERMS_URL))
                PaywallEvent.PrivacyClicked -> sendEffect(PaywallEffect.LaunchUri(PRIVACY_URL))
            }
        }

        private fun loadPlans() {
            setState { copy(status = PaywallStatus.Loading) }
            viewModelScope.launch {
                billingRepository
                    .loadPlans()
                    .onSuccess { offering ->
                        setState { copy(status = PaywallStatus.Ready(offering)) }
                    }.onFailure { error ->
                        // Developer-facing diagnostic only; the screen shows
                        // its own localized retryable error state.
                        Timber.tag(TAG).w(error, "Paywall offering load failed")
                        setState { copy(status = PaywallStatus.Error) }
                    }
            }
        }

        private fun purchase(activity: Activity) {
            // Only purchasable once the offering is loaded; ignore taps in
            // Loading/Error (the CTA isn't shown there anyway — defensive).
            val ready = uiState.value.status as? PaywallStatus.Ready ?: return
            // Single-flight: ignore a second tap while the Play sheet is up.
            if (uiState.value.isPurchasing) return

            val plan = ready.offering.planFor(uiState.value.selectedPlan)
            setState { copy(isPurchasing = true) }
            viewModelScope.launch {
                when (val result = billingRepository.purchase(activity, plan)) {
                    PurchaseResult.Success -> {
                        setState { copy(isPurchasing = false) }
                        sendEffect(PaywallEffect.Dismiss)
                    }
                    PurchaseResult.Cancelled ->
                        // Expected happy-path exit — back out silently, stay on the paywall.
                        setState { copy(isPurchasing = false) }
                    is PurchaseResult.Error -> {
                        Timber.tag(TAG).w("Paywall purchase failed: %s", result.message)
                        setState { copy(isPurchasing = false) }
                        sendEffect(PaywallEffect.ShowPurchaseError)
                    }
                }
            }
        }

        private fun restore() {
            // Single-flight: ignore a second tap while a sync is in flight.
            if (uiState.value.isRestoring) return
            setState { copy(isRestoring = true) }
            viewModelScope.launch {
                when (val result = billingRepository.restorePurchases()) {
                    is RestoreResult.Completed -> {
                        setState { copy(isRestoring = false) }
                        if (result.isPro) {
                            sendEffect(PaywallEffect.Dismiss)
                        } else {
                            sendEffect(PaywallEffect.ShowNothingToRestore)
                        }
                    }
                    is RestoreResult.Error -> {
                        Timber.tag(TAG).w("Paywall restore failed: %s", result.message)
                        setState { copy(isRestoring = false) }
                        sendEffect(PaywallEffect.ShowRestoreError)
                    }
                }
            }
        }

        private companion object {
            const val TAG = "PaywallVM"

            // Placeholder legal links. The live Terms / Privacy URLs are
            // finalized and verified against Play subscription policy in
            // nubecita-q5ge.11 (store/legal); the epic does not ship to
            // production until that task lands.
            const val TERMS_URL = "https://nubecita.app/terms"
            const val PRIVACY_URL = "https://nubecita.app/privacy"
        }
    }

/** Resolve the [SubscriptionPlan] matching [id] within this offering. */
private fun net.kikin.nubecita.data.models.SubscriptionOffering.planFor(id: SubscriptionPlanId): SubscriptionPlan =
    when (id) {
        SubscriptionPlanId.Monthly -> monthly
        SubscriptionPlanId.Annual -> annual
    }
