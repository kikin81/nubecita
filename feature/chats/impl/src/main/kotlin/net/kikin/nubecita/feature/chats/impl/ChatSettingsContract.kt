package net.kikin.nubecita.feature.chats.impl

import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Chat settings screen ("Who can message you").
 *
 * The screen has a single mutually-exclusive lifecycle (loading / loaded /
 * error), so per `CLAUDE.md`'s MVI conventions that lives in a sealed
 * [ChatSettingsLoadStatus] sum rather than independent boolean flags.
 */
data class ChatSettingsViewState(
    val status: ChatSettingsLoadStatus = ChatSettingsLoadStatus.Loading,
) : UiState

sealed interface ChatSettingsLoadStatus {
    /** Initial fetch of the current declaration in flight. */
    data object Loading : ChatSettingsLoadStatus

    /**
     * Declaration loaded. [selected] is the optimistic current choice;
     * [isSaving] is true while a write triggered by a tap is in flight (the
     * radio stays interactive — save-on-tap is latest-wins).
     */
    data class Loaded(
        val selected: AllowIncoming,
        val isSaving: Boolean = false,
    ) : ChatSettingsLoadStatus

    /** Initial fetch failed; the body offers a Retry. */
    data object LoadError : ChatSettingsLoadStatus
}

sealed interface ChatSettingsEvent : UiEvent {
    /** User tapped a radio option. Triggers an optimistic save-on-tap. */
    data class OptionSelected(
        val value: AllowIncoming,
    ) : ChatSettingsEvent

    /** User tapped Retry on the load-error state. */
    data object RetryLoad : ChatSettingsEvent
}

sealed interface ChatSettingsEffect : UiEffect {
    /** A save-on-tap write failed; the selection was reverted. Surface a snackbar. */
    data object ShowSaveError : ChatSettingsEffect
}
