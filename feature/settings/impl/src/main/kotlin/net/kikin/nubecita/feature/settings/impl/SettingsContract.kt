package net.kikin.nubecita.feature.settings.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.BillingPeriod

/**
 * MVI state for the Settings screen.
 *
 * Identity-header fields ([handle], [did], [displayName],
 * [avatarUrl]) are session-derived and populated by the VM. The VM
 * observes `SessionStateProvider.state` via
 * `filterIsInstance<SignedIn>().take(1).launchIn(viewModelScope)` —
 * meaning the first SignedIn emission (typically immediate, since
 * the StateFlow is hot and resolved by the time Settings is
 * reachable) populates [handle] and [did] together
 * ([did] feeds the avatar fallback via `NubecitaAvatar`'s own
 * `avatarFallbackFor` recomputation). [displayName] and
 * [avatarUrl] arrive separately after an
 * `ActorProfileRepository.fetchProfile` round-trip and stay null on
 * fetch failure (header still renders — greeting falls back to "Hi!",
 * avatar to the initials disc).
 *
 * Because the flow-based init queues onto the coroutine dispatcher,
 * the very first composition may briefly render with `handle = null`
 * (one-frame window). The composable's empty-string fallback handles
 * this defensively.
 *
 * `confirmDialogOpen` is **flat** because dialog visibility is
 * independent of the sign-out status (a dialog can be open while idle
 * OR while signing out — the latter shows a spinner inside the dialog).
 *
 * `status` is a **sealed sum** because Idle / SigningOut are mutually
 * exclusive — at any given moment exactly one is true.
 */
data class SettingsViewState(
    val handle: String? = null,
    val did: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val confirmDialogOpen: Boolean = false,
    val status: SettingsStatus = SettingsStatus.Idle,
    /**
     * Nubecita Pro entitlement, mirrored from `EntitlementRepository.isPro`.
     * Drives the "Nubecita Pro" section's two faces: false → an upsell row
     * that opens the paywall; true → manage-subscription + restore rows.
     */
    val isPro: Boolean = false,
    /**
     * The active plan's billing period, for the Pro section's current-plan
     * caption ("Annual" / "Monthly"). Null until resolved or when the active
     * base plan isn't recognized — the row then shows a neutral "Active"
     * caption. Kept structured (not a pre-built string) so the screen composes
     * the localized caption via `stringResource`; the VM has no Context.
     */
    val currentPlanPeriod: BillingPeriod? = null,
    /**
     * The active plan's store-localized price string (e.g. `"$19.99"`), paired
     * with [currentPlanPeriod] to render "Annual · $19.99/yr". Null until a
     * one-shot `loadPlans()` cross-reference resolves it (or on failure).
     */
    val currentPlanFormattedPrice: String? = null,
    /**
     * Store product id of the active Pro subscription, used as the `sku` in the
     * Play manage-subscription deep link. Null → the deep link falls back to the
     * package-level Play subscriptions page.
     */
    val manageSku: String? = null,
    /** True while a Restore-purchases request is in flight (single-flight guard + row spinner). */
    val isRestoring: Boolean = false,
    /**
     * The single "check for new messages" toggle (design D6), mirrored from
     * `MessageCheckingPreference`. Default true. When off, BOTH the foreground
     * unread poller AND the background DM-notification worker stop — drives the
     * Notifications-section Switch row.
     */
    val messageCheckingEnabled: Boolean = true,
) : UiState

/**
 * Sign-out lifecycle. No `SignedOut` variant: on success, the
 * `SessionStateProvider` transitions, `MainActivity`'s reactive
 * collector does `navigator.replaceTo(Login)`, and this screen
 * unmounts before we'd ever render a "signed out" state.
 */
sealed interface SettingsStatus {
    data object Idle : SettingsStatus

    data object SigningOut : SettingsStatus
}

/**
 * Events the screen sends to the ViewModel.
 */
sealed interface SettingsEvent : UiEvent {
    /** User tapped the Sign Out row. Opens the confirmation dialog. */
    data object SignOutTapped : SettingsEvent

    /** User tapped Confirm inside the dialog. Kicks off the sign-out request. */
    data object ConfirmSignOut : SettingsEvent

    /** User tapped Cancel inside the dialog or tapped outside (scrim). */
    data object DismissDialog : SettingsEvent

    /**
     * User tapped the header's "Manage your Bluesky account" pill.
     * VM responds with a [SettingsEffect.LaunchUri] pointing at the
     * hosted web settings page.
     */
    data object ManageAccountTapped : SettingsEvent

    /** User tapped the "Terms of Service" row → [SettingsEffect.LaunchUri] to the hosted terms page. */
    data object TermsTapped : SettingsEvent

    /** User tapped the "Privacy Policy" row → [SettingsEffect.LaunchUri] to the hosted privacy page. */
    data object PrivacyTapped : SettingsEvent

    /**
     * User tapped the "Delete account" row. Account + content deletion lives on
     * Bluesky (the data is on the PDS, not in Nubecita), so the VM opens the
     * hosted Bluesky settings page via [SettingsEffect.LaunchUri].
     */
    data object DeleteAccountTapped : SettingsEvent

    /**
     * User tapped the Switch-account placeholder row. Multi-account auth
     * is out of scope for this epic; VM responds with a coming-soon
     * snackbar effect. When multi-account ships, swap the effect for the
     * real account-picker NavKey push.
     */
    data object SwitchAccountTapped : SettingsEvent

    /**
     * User tapped the Notifications row. v1 deep-links to the OS app
     * notification settings (per-channel toggles, badge controls,
     * importance overrides) — Nubecita's own in-app per-reason toggles
     * land in a later epic. VM responds with
     * [SettingsEffect.OpenSystemNotificationSettings].
     */
    data object NotificationsTapped : SettingsEvent

    /**
     * User toggled the "Check for new messages" switch (design D6). VM persists
     * it to `MessageCheckingPreference`; the change reactively starts/stops both
     * the foreground unread poller and the background DM-notification worker.
     */
    data class MessageCheckingToggled(
        val enabled: Boolean,
    ) : SettingsEvent

    /**
     * User tapped the "Nubecita Pro" upsell row (non-Pro). VM responds with
     * [SettingsEffect.OpenPaywall]; the screen pushes `PaywallRoute` onto the
     * MainShell inner back stack (nav is a screen concern, never the VM's).
     */
    data object ProUpsellTapped : SettingsEvent

    /**
     * User tapped "Manage subscription" (Pro). VM responds with
     * [SettingsEffect.OpenManageSubscription] carrying the active sku; the
     * screen builds the Play deep link (it owns the package name) and launches.
     */
    data object ManageSubscriptionTapped : SettingsEvent

    /**
     * User tapped "Restore purchases" (Pro). VM calls
     * `BillingRepository.restorePurchases()` and surfaces the outcome as a
     * snackbar effect; entitlement changes propagate via the `isPro` stream.
     */
    data object RestorePurchasesTapped : SettingsEvent

    /**
     * User tapped the "Follow the developer" row (About section). VM responds
     * with [SettingsEffect.NavigateToDeveloperProfile]; the screen pushes the
     * developer's `Profile` NavKey onto the MainShell inner back stack (nav is
     * a screen concern, never the VM's — same as [ProUpsellTapped]).
     */
    data object FollowDeveloperTapped : SettingsEvent

    /**
     * User tapped the "About" row. VM responds with [SettingsEffect.OpenAbout];
     * the screen pushes the `About` NavKey onto the MainShell inner back stack.
     */
    data object AboutTapped : SettingsEvent

    /**
     * User tapped the "Moderation" row. VM responds with
     * [SettingsEffect.OpenModeration]; the screen pushes the `Moderation` hub
     * NavKey (content filters + blocked accounts) onto the inner back stack.
     */
    data object ModerationTapped : SettingsEvent
}

/**
 * One-shot effects collected by the screen.
 */
sealed interface SettingsEffect : UiEffect {
    /** Sign-out failed. Surface a snackbar; copy is resolved at render time. */
    data object ShowSignOutError : SettingsEffect

    /**
     * Open an external URL (via the system's preferred handler — Chrome
     * Custom Tab when installed, system browser otherwise). Used for
     * the Manage-Account pill and the future web-forwarding rows
     * (Muted words / Blocked accounts / Terms / Privacy).
     */
    data class LaunchUri(
        val uri: String,
    ) : SettingsEffect

    /**
     * Surface the "Multi-account coming soon" snackbar. Distinct from
     * a generic snackbar effect so the screen can resolve the localized
     * string at render time (same pattern as [ShowSignOutError]).
     */
    data object ShowSwitchAccountComingSoon : SettingsEffect

    /**
     * Fire `Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)` with the
     * app's package as `EXTRA_APP_PACKAGE`, deep-linking the user into
     * the OS-level per-channel notification settings page for Nubecita.
     *
     * Distinct from [LaunchUri] because that effect is bound to Chrome
     * Custom Tabs (https URLs), while this one launches a system
     * `android.settings.*` activity directly. Screen-side handler builds
     * the intent and calls `context.startActivity(...)`.
     */
    data object OpenSystemNotificationSettings : SettingsEffect

    /**
     * Push the Nubecita Pro paywall (`PaywallRoute`) onto MainShell's inner
     * back stack. The VM never holds the nav state — the screen collects this
     * and calls its `onNavigateTo(PaywallRoute)` callback (wired by the nav
     * module to `LocalMainShellNavState.current.add(...)`), matching the
     * profile → sub-route pattern.
     */
    data object OpenPaywall : SettingsEffect

    /**
     * Open the Google Play manage-subscription page. The screen builds
     * `https://play.google.com/store/account/subscriptions?package=<pkg>` (it
     * owns the package name) and appends `&sku=<sku>` when [sku] is non-null to
     * deep-link straight to this subscription; a null [sku] lands on the
     * package's subscription list. Launched via the same CustomTabsIntent path
     * as [LaunchUri].
     */
    data class OpenManageSubscription(
        val sku: String?,
    ) : SettingsEffect

    /** Restore found an active subscription — confirm with a snackbar. */
    data object ShowRestoreSuccess : SettingsEffect

    /** Restore completed but found nothing to restore — friendly snackbar, not an error. */
    data object ShowNothingToRestore : SettingsEffect

    /** Restore failed (network/provider) — error snackbar. */
    data object ShowRestoreError : SettingsEffect

    /**
     * Push the developer's `Profile` route onto MainShell's inner back stack.
     * Payload-free (the developer's DID is a screen-owned constant, mirroring
     * how [OpenPaywall] keeps `PaywallRoute` on the screen side); the screen
     * collects this and calls `onNavigateTo(Profile(handle = <dev DID>))`,
     * wired by the nav module to `LocalMainShellNavState.current.add(...)`.
     */
    data object NavigateToDeveloperProfile : SettingsEffect

    /**
     * Push the `About` route onto MainShell's inner back stack. Payload-free
     * (the screen owns the `About` NavKey, like [OpenPaywall]); the screen
     * collects this and calls `onNavigateTo(About)`.
     */
    data object OpenAbout : SettingsEffect

    /**
     * Push the `Moderation` hub route onto MainShell's inner back stack.
     * Payload-free (the screen owns the `Moderation` NavKey, like [OpenAbout]);
     * the screen collects this and calls `onNavigateTo(Moderation)`.
     */
    data object OpenModeration : SettingsEffect
}
