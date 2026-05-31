package net.kikin.nubecita.feature.profile.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/** `app.bsky.actor.profile` display-name limit (graphemes). */
internal const val MAX_DISPLAY_NAME_GRAPHEMES = 64

/** `app.bsky.actor.profile` description limit (graphemes). */
internal const val MAX_DESCRIPTION_GRAPHEMES = 256

/**
 * Flat UI state for the EditProfile screen. Text fields are owned here (plain
 * MVI — no cursor-aware reducer work, so no `TextFieldState` needed): the
 * grapheme counts are projected on each change by the reducer. The over-limit
 * and `canSave` flags are derived getters (they don't affect data-class
 * equality, so recomposition still skips on the stored fields).
 */
internal data class EditProfileViewState(
    val displayName: String = "",
    val description: String = "",
    val displayNameGraphemes: Int = 0,
    val descriptionGraphemes: Int = 0,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val showDiscardDialog: Boolean = false,
) : UiState {
    val isDisplayNameOverLimit: Boolean get() = displayNameGraphemes > MAX_DISPLAY_NAME_GRAPHEMES
    val isDescriptionOverLimit: Boolean get() = descriptionGraphemes > MAX_DESCRIPTION_GRAPHEMES

    /** Save is allowed only with pending, in-limit edits and no in-flight write. */
    val canSave: Boolean
        get() = isDirty && !isSaving && !isDisplayNameOverLimit && !isDescriptionOverLimit
}

internal sealed interface EditProfileEvent : UiEvent {
    data class DisplayNameChanged(
        val value: String,
    ) : EditProfileEvent

    data class DescriptionChanged(
        val value: String,
    ) : EditProfileEvent

    data object SaveTapped : EditProfileEvent

    /** Back affordance (app-bar up or system back). Gated by the dirty guard. */
    data object BackPressed : EditProfileEvent

    data object DiscardConfirmed : EditProfileEvent

    data object DiscardDismissed : EditProfileEvent
}

internal sealed interface EditProfileEffect : UiEffect {
    /** Pop the EditProfile sub-route off MainShell's back stack. */
    data object NavigateBack : EditProfileEffect

    data class ShowError(
        val error: SaveError,
    ) : EditProfileEffect
}

/** Typed save failures; the screen maps each to a user-facing string resource. */
internal enum class SaveError {
    /** Profile changed elsewhere — stale swap CID. Prompt reload-and-retry. */
    SwapConflict,

    /** No signed-in session. */
    Unauthorized,

    /** Network / write failure. */
    Network,
}
