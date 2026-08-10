package net.kikin.nubecita.feature.settings.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.preferences.AutoplayPreference

/**
 * Flat state for the Media and animations screen — a direct projection of the
 * two stored preferences.
 *
 * Deliberately the stored [AutoplayPreference], not "may video autoplay right
 * now". This screen edits *intent*; whether a given video actually plays also
 * depends on the connection, and that lives in `AutoplayPolicy`. Showing the
 * resolved answer here would make the selected row flicker as the user walked
 * off wifi.
 *
 * No sealed status sum: both preference flows always emit, so there is no
 * loading, error, or empty mode.
 */
internal data class MediaAndAnimationsState(
    val autoplay: AutoplayPreference,
    val autoplayGifs: Boolean,
) : UiState

internal sealed interface MediaAndAnimationsEvent : UiEvent {
    /**
     * User tapped a video-autoplay row. Fires on **every** tap including the
     * already-selected one — `NubecitaListItem.onSelect` has no re-tap guard by
     * design, and its KDoc puts that guard on the ViewModel.
     */
    data class AutoplaySelected(
        val preference: AutoplayPreference,
    ) : MediaAndAnimationsEvent

    /** User toggled the GIF-autoplay switch. */
    data class AutoplayGifsToggled(
        val enabled: Boolean,
    ) : MediaAndAnimationsEvent
}

internal sealed interface MediaAndAnimationsEffect : UiEffect {
    /** A preference write failed; the screen surfaces it as a snackbar. */
    data object ShowSaveError : MediaAndAnimationsEffect
}
