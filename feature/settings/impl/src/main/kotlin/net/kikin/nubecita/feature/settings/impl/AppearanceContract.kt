package net.kikin.nubecita.feature.settings.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.preferences.ThemePreference

/**
 * Flat state for the Appearance screen — a direct projection of the stored
 * theme preference.
 *
 * [selected] is the **persisted** [ThemePreference], not the design system's
 * `AppTheme`. This screen edits storage; `AppTheme` is the rendering identity
 * the composition root derives from it, and translating here would mean mapping
 * back on write for no gain. The option list the screen renders is
 * `ThemePreference.entries`, whose declaration order is already the required
 * display order (Dynamic, Light, Dark).
 *
 * No sealed status sum: there is no loading, error, or empty mode. The
 * preference flow always emits, so the screen is never in an indeterminate
 * state.
 */
internal data class AppearanceState(
    val selected: ThemePreference,
) : UiState

internal sealed interface AppearanceEvent : UiEvent {
    /**
     * User tapped a theme row.
     *
     * Fires on **every** tap, including the already-selected row —
     * `NubecitaListItem`'s `onSelect` has no re-tap guard by design, and its
     * KDoc puts that guard on the ViewModel so it survives a swap of the
     * control. [AppearanceViewModel] short-circuits accordingly.
     */
    data class ThemeSelected(
        val theme: ThemePreference,
    ) : AppearanceEvent
}

internal sealed interface AppearanceEffect : UiEffect {
    /** Persisting the choice failed — surface a snackbar. */
    data object ShowSaveError : AppearanceEffect
}
