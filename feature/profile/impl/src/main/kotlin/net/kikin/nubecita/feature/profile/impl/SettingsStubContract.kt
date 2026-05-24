package net.kikin.nubecita.feature.profile.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Settings stub screen.
 *
 * Identity-header fields ([handle], [avatarHue], [displayName],
 * [avatarUrl]) are session-derived and populated by the VM. The VM
 * observes `SessionStateProvider.state` via
 * `filterIsInstance<SignedIn>().take(1).launchIn(viewModelScope)` —
 * meaning the first SignedIn emission (typically immediate, since
 * the StateFlow is hot and resolved by the time Settings is
 * reachable) populates [handle] AND [avatarHue] together (the latter
 * computed via `feature/profile/impl/data/AuthorProfileMapper.
 * avatarHueFor(did, handle)` so it matches the same user's Profile /
 * Chats avatar). [displayName] and [avatarUrl] arrive separately
 * after a `ProfileRepository.fetchHeader` round-trip and stay null
 * on fetch failure (header still renders — greeting falls back to
 * "Hi!", avatar to the initials disc).
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
data class SettingsStubViewState(
    val handle: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val avatarHue: Int = 0,
    val confirmDialogOpen: Boolean = false,
    val status: SettingsStubStatus = SettingsStubStatus.Idle,
) : UiState

/**
 * Sign-out lifecycle. No `SignedOut` variant: on success, the
 * `SessionStateProvider` transitions, `MainActivity`'s reactive
 * collector does `navigator.replaceTo(Login)`, and this screen
 * unmounts before we'd ever render a "signed out" state.
 */
sealed interface SettingsStubStatus {
    data object Idle : SettingsStubStatus

    data object SigningOut : SettingsStubStatus
}

/**
 * Events the screen sends to the ViewModel.
 */
sealed interface SettingsStubEvent : UiEvent {
    /** User tapped the Sign Out row. Opens the confirmation dialog. */
    data object SignOutTapped : SettingsStubEvent

    /** User tapped Confirm inside the dialog. Kicks off the sign-out request. */
    data object ConfirmSignOut : SettingsStubEvent

    /** User tapped Cancel inside the dialog or tapped outside (scrim). */
    data object DismissDialog : SettingsStubEvent

    /**
     * User tapped the header's "Manage your Bluesky account" pill.
     * VM responds with a [SettingsStubEffect.LaunchUri] pointing at the
     * hosted web settings page.
     */
    data object ManageAccountTapped : SettingsStubEvent

    /**
     * User tapped the Switch-account placeholder row. Multi-account auth
     * is out of scope for this epic; VM responds with a coming-soon
     * snackbar effect. When multi-account ships, swap the effect for the
     * real account-picker NavKey push.
     */
    data object SwitchAccountTapped : SettingsStubEvent
}

/**
 * One-shot effects collected by the screen.
 */
sealed interface SettingsStubEffect : UiEffect {
    /** Sign-out failed. Surface a snackbar; copy is resolved at render time. */
    data object ShowSignOutError : SettingsStubEffect

    /**
     * Open an external URL (via the system's preferred handler — Chrome
     * Custom Tab when installed, system browser otherwise). Used for
     * the Manage-Account pill and the future web-forwarding rows
     * (Muted words / Blocked accounts / Terms / Privacy).
     */
    data class LaunchUri(
        val uri: String,
    ) : SettingsStubEffect

    /**
     * Surface the "Multi-account coming soon" snackbar. Distinct from
     * a generic snackbar effect so the screen can resolve the localized
     * string at render time (same pattern as [ShowSignOutError]).
     */
    data object ShowSwitchAccountComingSoon : SettingsStubEffect
}
