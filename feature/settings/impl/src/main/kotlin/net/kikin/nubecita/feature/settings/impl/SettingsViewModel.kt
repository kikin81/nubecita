package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.billing.BillingRepository
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.billing.RestoreResult
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.profile.ActorProfileRepository
import net.kikin.nubecita.core.profile.avatarHueFor
import net.kikin.nubecita.data.models.ActiveSubscription
import net.kikin.nubecita.data.models.SubscriptionPlanId
import timber.log.Timber
import javax.inject.Inject

/**
 * Presenter for the Settings screen. Owns:
 *
 * - The sign-out flow via [AuthRepository.signOut]: tap → confirmation
 *   dialog → confirm → loading state → success (screen unmounts when
 *   SessionStateProvider transitions and MainActivity replaces to
 *   Login) OR failure (error snackbar).
 * - The identity-header state. On init, populates [SettingsViewState.handle]
 *   synchronously from [SessionStateProvider.state] and fires a
 *   [ActorProfileRepository.fetchProfile] coroutine to fill in
 *   displayName + avatarUrl. Fetch failures are silent — the header
 *   still renders (greeting → "Hi!"; avatar → initials disc derived
 *   from the handle).
 * - The Manage-Account and Switch-account tap effects (LaunchUri and
 *   ShowSwitchAccountComingSoon respectively).
 *
 * Per CLAUDE.md MVI conventions: state is flat (no Async wrapper);
 * inline `viewModelScope.launch { try ... } catch` rather than a base-
 * class helper.
 */
@HiltViewModel
internal class SettingsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        sessionStateProvider: SessionStateProvider,
        private val actorProfileRepository: ActorProfileRepository,
        private val entitlementRepository: EntitlementRepository,
        private val billingRepository: BillingRepository,
    ) : MviViewModel<SettingsViewState, SettingsEvent, SettingsEffect>(
            SettingsViewState(),
        ) {
        init {
            // Observe the first SignedIn emission instead of snapshotting
            // .state.value at init time. The earlier snapshot was correct
            // for the typical Login → MainShell handoff (state is already
            // SignedIn by then) but silently left the header empty if the
            // VM was constructed during any window where state is still
            // Loading / SignedOut — process-death restore where MainShell
            // restores Settings on the back stack before the session has
            // rehydrated, future deep-link paths, etc. take(1) makes this
            // a one-shot read once SignedIn arrives; sign-out still
            // unmounts the screen via the outer Navigator before any
            // SignedIn → SignedOut transition matters, so we don't need
            // to keep observing.
            sessionStateProvider.state
                .filterIsInstance<SessionState.SignedIn>()
                .take(1)
                .onEach { signedIn ->
                    // Hue derived from (did, handle) via the same helper
                    // AuthorProfileMapper / ConvoMapper use, so the same
                    // user's avatar paints identically across Settings,
                    // Profile, and Chats. Crucially, hue is fixed by the
                    // session-stable identity — it doesn't re-shift when
                    // the displayName later arrives via fetchHeader.
                    setState {
                        copy(
                            handle = signedIn.handle,
                            avatarHue = avatarHueFor(did = signedIn.did, handle = signedIn.handle),
                        )
                    }
                    fetchProfile(signedIn.did)
                }.launchIn(viewModelScope)

            // Mirror the Pro entitlement into flat state for the "Nubecita Pro"
            // section. isPro is a hot StateFlow (starts false, never throws).
            entitlementRepository.isPro
                .onEach { isPro -> setState { copy(isPro = isPro) } }
                .launchIn(viewModelScope)

            // The active subscription drives the manage-subscription sku and the
            // current-plan label. It's a StateFlow (already conflated + distinct),
            // so each distinct emission triggers at most one price resolution.
            entitlementRepository.activeSubscription
                .onEach { active ->
                    setState { copy(manageSku = active?.productId) }
                    resolvePlanLabel(active)
                }.launchIn(viewModelScope)
        }

        override fun handleEvent(event: SettingsEvent) {
            when (event) {
                SettingsEvent.SignOutTapped ->
                    setState { copy(confirmDialogOpen = true) }
                SettingsEvent.DismissDialog ->
                    setState { copy(confirmDialogOpen = false) }
                SettingsEvent.ConfirmSignOut ->
                    runSignOut()
                SettingsEvent.ManageAccountTapped ->
                    sendEffect(SettingsEffect.LaunchUri(uri = MANAGE_ACCOUNT_URL))
                SettingsEvent.SwitchAccountTapped ->
                    sendEffect(SettingsEffect.ShowSwitchAccountComingSoon)
                SettingsEvent.NotificationsTapped ->
                    sendEffect(SettingsEffect.OpenSystemNotificationSettings)
                SettingsEvent.ProUpsellTapped ->
                    sendEffect(SettingsEffect.OpenPaywall)
                SettingsEvent.ManageSubscriptionTapped ->
                    sendEffect(SettingsEffect.OpenManageSubscription(sku = uiState.value.manageSku))
                SettingsEvent.RestorePurchasesTapped ->
                    runRestore()
            }
        }

        /**
         * Resolve the current-plan caption inputs for the Pro section. The
         * active subscription carries the plan id but not its price, so we
         * cross-reference `loadPlans()` for the store-localized price of that
         * plan and expose `period` + `formattedPrice` structurally (the screen
         * composes the localized caption). Best-effort: a null plan id
         * (unrecognized base plan) or a failed offering load leaves both null,
         * and the row falls back to a neutral "Active" caption.
         */
        private fun resolvePlanLabel(active: ActiveSubscription?) {
            val planId = active?.planId
            if (planId == null) {
                setState { copy(currentPlanPeriod = null, currentPlanFormattedPrice = null) }
                return
            }
            viewModelScope.launch {
                billingRepository
                    .loadPlans()
                    .onSuccess { offering ->
                        val plan = if (planId == SubscriptionPlanId.Annual) offering.annual else offering.monthly
                        setState {
                            copy(
                                currentPlanPeriod = plan.period,
                                currentPlanFormattedPrice = plan.formattedPrice,
                            )
                        }
                    }.onFailure {
                        // Keep the section usable without the price — the row
                        // shows "Active". No user-facing error for a label.
                        Timber.tag(TAG).w(it, "Pro plan-label resolution failed")
                        setState { copy(currentPlanPeriod = null, currentPlanFormattedPrice = null) }
                    }
            }
        }

        private fun runRestore() {
            if (uiState.value.isRestoring) return
            setState { copy(isRestoring = true) }
            viewModelScope.launch {
                try {
                    when (val result = billingRepository.restorePurchases()) {
                        is RestoreResult.Completed ->
                            sendEffect(
                                if (result.isPro) {
                                    SettingsEffect.ShowRestoreSuccess
                                } else {
                                    SettingsEffect.ShowNothingToRestore
                                },
                            )
                        is RestoreResult.Error -> {
                            Timber.tag(TAG).w("Settings restore failed: %s", result.message)
                            sendEffect(SettingsEffect.ShowRestoreError)
                        }
                    }
                } finally {
                    setState { copy(isRestoring = false) }
                }
            }
        }

        private fun fetchProfile(actor: String) {
            viewModelScope.launch {
                actorProfileRepository
                    .fetchProfile(actor)
                    .onSuccess { profile ->
                        setState {
                            copy(
                                displayName = profile.displayName,
                                avatarUrl = profile.avatarUrl,
                            )
                        }
                    }.onFailure { error ->
                        // Header still renders without these fields —
                        // greeting falls back to "Hi!", avatar to the
                        // initials disc. No user-facing error needed.
                        Timber.tag(TAG).w(error, "Settings header profile fetch failed")
                    }
            }
        }

        private fun runSignOut() {
            // Single-flight: ignore a second Confirm tap while the first
            // request is still in flight.
            if (uiState.value.status is SettingsStatus.SigningOut) return
            setState { copy(status = SettingsStatus.SigningOut) }
            viewModelScope.launch {
                authRepository
                    .signOut()
                    .onFailure {
                        setState {
                            copy(
                                confirmDialogOpen = false,
                                status = SettingsStatus.Idle,
                            )
                        }
                        sendEffect(SettingsEffect.ShowSignOutError)
                    }
                // No onSuccess: SessionStateProvider transitions →
                // MainActivity's reactive collector replaces to Login →
                // this VM is scrapped. setState after success would
                // race the unmount.
            }
        }

        private companion object {
            const val TAG = "SettingsVM"
            const val MANAGE_ACCOUNT_URL = "https://bsky.app/settings"
        }
    }
