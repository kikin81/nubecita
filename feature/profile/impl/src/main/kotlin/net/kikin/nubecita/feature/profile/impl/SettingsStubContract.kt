package net.kikin.nubecita.feature.profile.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Settings stub screen.
 *
 * `confirmDialogOpen` is **flat** because dialog visibility is
 * independent of the sign-out status (a dialog can be open while idle
 * OR while signing out — the latter shows a spinner inside the dialog).
 *
 * `status` is a **sealed sum** because Idle / SigningOut are mutually
 * exclusive — at any given moment exactly one is true.
 */
data class SettingsStubViewState(
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
    /** User tapped the Sign Out button. Opens the confirmation dialog. */
    data object SignOutTapped : SettingsStubEvent

    /** User tapped Confirm inside the dialog. Kicks off the sign-out request. */
    data object ConfirmSignOut : SettingsStubEvent

    /** User tapped Cancel inside the dialog or tapped outside (scrim). */
    data object DismissDialog : SettingsStubEvent
}

/**
 * One-shot effects. There is exactly one — error surfacing on
 * sign-out failure. Success has no effect: the screen unmounts when
 * the outer Navigator replaces to Login.
 */
sealed interface SettingsStubEffect : UiEffect {
    /** Sign-out failed. Surface a snackbar; copy is resolved at render time. */
    data object ShowSignOutError : SettingsStubEffect
}
