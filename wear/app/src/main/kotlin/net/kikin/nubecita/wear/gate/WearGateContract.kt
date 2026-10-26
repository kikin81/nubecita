package net.kikin.nubecita.wear.gate

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * What the watch can show before (or instead of) content. Exactly one applies,
 * so it is a status sum rather than flags.
 */
sealed interface WearGateStatus {
    data object Loading : WearGateStatus

    /** Android phone without Nubecita: offer the Play Store install on the phone. */
    data object PhoneAppMissing : WearGateStatus

    /** Paired with an iPhone: Nubecita for Wear needs the Android phone app. */
    data object NeedsAndroidPhone : WearGateStatus

    data object SignedOut : WearGateStatus

    /** The phone speaks a newer contract version than this watch app. */
    data object UpdateRequired : WearGateStatus

    data class SignedIn(
        val handle: String,
    ) : WearGateStatus
}

data class WearGateState(
    val status: WearGateStatus = WearGateStatus.Loading,
    /** The "continue on phone" confirmation after something was opened there. */
    val showOpenOnPhone: Boolean = false,
) : UiState

sealed interface WearGateEvent : UiEvent {
    /** The app came to the foreground: re-check the phone and ask it to sync. */
    data object Resumed : WearGateEvent

    data object OpenOnPhoneClicked : WearGateEvent

    data object OpenOnPhoneDismissed : WearGateEvent
}

/** No one-shot effects yet; screens react to [WearGateState] alone. */
sealed interface WearGateEffect : UiEffect
