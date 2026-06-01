package net.kikin.nubecita.feature.paywall.impl

import android.app.Activity
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.PaywallCheckoutStarted
import net.kikin.nubecita.core.analytics.PaywallPlan
import net.kikin.nubecita.core.analytics.PaywallPlanSelected
import net.kikin.nubecita.core.analytics.PaywallPurchaseCancelled
import net.kikin.nubecita.core.analytics.PaywallPurchaseError
import net.kikin.nubecita.core.analytics.PaywallRestore
import net.kikin.nubecita.core.analytics.PaywallViewed
import net.kikin.nubecita.core.analytics.RestoreOutcome
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
        private val analytics: AnalyticsClient,
    ) : MviViewModel<PaywallState, PaywallEvent, PaywallEffect>(PaywallState()) {
        init {
            // Fired once when the paywall is presented (top of the funnel).
            analytics.log(PaywallViewed)
            loadPlans()
        }

        override fun handleEvent(event: PaywallEvent) {
            when (event) {
                PaywallEvent.Retry -> loadPlans()
                is PaywallEvent.PlanSelected -> {
                    analytics.log(PaywallPlanSelected(event.planId.toAnalyticsPlan()))
                    setState { copy(selectedPlan = event.planId) }
                }
                is PaywallEvent.PurchaseClicked -> purchase(event.activity)
                PaywallEvent.RestoreClicked -> restore()
                PaywallEvent.TermsClicked -> sendEffect(PaywallEffect.LaunchUri(TERMS_URL))
                PaywallEvent.PrivacyClicked -> sendEffect(PaywallEffect.LaunchUri(PRIVACY_URL))
            }
        }

        private fun loadPlans() {
            setState { copy(status = PaywallStatus.Loading) }
            viewModelScope.launch {
                // loadPlans is contractually a Result, but a misbehaving
                // provider impl could still throw — guard so an unexpected
                // throw routes to the retryable Error state instead of
                // leaving the UI stuck in Loading. CancellationException is
                // rethrown so normal scope teardown isn't swallowed.
                try {
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
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Paywall offering load threw unexpectedly")
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
            // Checkout boundary: control is about to hand to the Play sheet. The
            // converted purchase/revenue event comes from RevenueCat server-side
            // (q5ge.13), so the client logs only this start + the non-converting
            // outcomes below.
            analytics.log(PaywallCheckoutStarted(plan.id.toAnalyticsPlan()))
            setState { copy(isPurchasing = true) }
            viewModelScope.launch {
                // try/finally guarantees the spinner clears even if the
                // billing layer throws (otherwise the CTA stays permanently
                // disabled); the catch turns an unexpected throw into the same
                // user-facing error as a PurchaseResult.Error rather than a
                // crash. CancellationException is rethrown so the finally
                // still runs but scope teardown isn't swallowed.
                try {
                    when (val result = billingRepository.purchase(activity, plan)) {
                        // No client success event: the terminal purchase/revenue
                        // event is owned by RevenueCat's server-side GA4 link.
                        // PurchaseSucceeded (not Dismiss) routes to the thank-you
                        // screen — only a fresh purchase celebrates; restore below
                        // still emits Dismiss.
                        PurchaseResult.Success -> sendEffect(PaywallEffect.PurchaseSucceeded)
                        PurchaseResult.Cancelled -> analytics.log(PaywallPurchaseCancelled)
                        is PurchaseResult.Error -> {
                            Timber.tag(TAG).w("Paywall purchase failed: %s", result.message)
                            analytics.log(PaywallPurchaseError)
                            sendEffect(PaywallEffect.ShowPurchaseError)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Paywall purchase threw unexpectedly")
                    analytics.log(PaywallPurchaseError)
                    sendEffect(PaywallEffect.ShowPurchaseError)
                } finally {
                    setState { copy(isPurchasing = false) }
                }
            }
        }

        private fun restore() {
            // Single-flight: ignore a second tap while a sync is in flight.
            if (uiState.value.isRestoring) return
            setState { copy(isRestoring = true) }
            viewModelScope.launch {
                // Same guard shape as purchase: finally clears the spinner so
                // the Restore action can't get stuck; the catch maps an
                // unexpected throw to the restore-error snackbar.
                try {
                    when (val result = billingRepository.restorePurchases()) {
                        is RestoreResult.Completed ->
                            if (result.isPro) {
                                analytics.log(PaywallRestore(RestoreOutcome.Restored))
                                sendEffect(PaywallEffect.Dismiss)
                            } else {
                                analytics.log(PaywallRestore(RestoreOutcome.Nothing))
                                sendEffect(PaywallEffect.ShowNothingToRestore)
                            }
                        is RestoreResult.Error -> {
                            Timber.tag(TAG).w("Paywall restore failed: %s", result.message)
                            analytics.log(PaywallRestore(RestoreOutcome.Error))
                            sendEffect(PaywallEffect.ShowRestoreError)
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Paywall restore threw unexpectedly")
                    analytics.log(PaywallRestore(RestoreOutcome.Error))
                    sendEffect(PaywallEffect.ShowRestoreError)
                } finally {
                    setState { copy(isRestoring = false) }
                }
            }
        }

        private companion object {
            const val TAG = "PaywallVM"

            // Live legal pages on nubecita.app (GitHub Pages, repo
            // nubecita-web). The page *content* must be updated to cover the
            // Pro subscription (auto-renewal, billing-through-Play, refunds;
            // purchase data + RevenueCat as processor) before store
            // submission — tracked in nubecita-q5ge.11.
            const val TERMS_URL = "https://nubecita.app/terms/"
            const val PRIVACY_URL = "https://nubecita.app/privacy-policy/"
        }
    }

/** Resolve the [SubscriptionPlan] matching [id] within this offering. */
private fun net.kikin.nubecita.data.models.SubscriptionOffering.planFor(id: SubscriptionPlanId): SubscriptionPlan =
    when (id) {
        SubscriptionPlanId.Monthly -> monthly
        SubscriptionPlanId.Annual -> annual
    }

/** Map the domain plan id onto the analytics enum (keeps `:core:analytics` free of `:data:models`). */
private fun SubscriptionPlanId.toAnalyticsPlan(): PaywallPlan =
    when (this) {
        SubscriptionPlanId.Monthly -> PaywallPlan.Monthly
        SubscriptionPlanId.Annual -> PaywallPlan.Annual
    }
