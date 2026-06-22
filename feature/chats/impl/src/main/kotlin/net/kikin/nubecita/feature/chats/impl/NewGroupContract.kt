package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

/**
 * MVI state for the new-group creation picker.
 *
 * Forks [AddGroupMembersViewState] for the recipient-picker half ([selected]
 * chips, [atCapacity] gate, [status] search lifecycle) and adds the group-name
 * editor projections ([nameGraphemeCount] / [isNameValid]) plus an
 * [isSubmitting] input-lock that holds while `createGroup` is in flight.
 *
 * [canCreate] is the single gate the submit button reads: a valid name, at
 * least one picked recipient, and not already submitting.
 */
@Immutable
data class NewGroupViewState(
    val selected: ImmutableList<RecipientUi> = persistentListOf(),
    val atCapacity: Boolean = false,
    val nameGraphemeCount: Int = 0,
    val isNameValid: Boolean = false,
    val isSubmitting: Boolean = false,
    val status: NewGroupStatus = NewGroupStatus.Recent(persistentListOf()),
) : UiState {
    val canCreate: Boolean get() = isNameValid && selected.isNotEmpty() && !isSubmitting
}

sealed interface NewGroupStatus {
    data class Recent(
        val items: ImmutableList<ActorUi>,
    ) : NewGroupStatus

    data object Searching : NewGroupStatus

    data class Results(
        val items: ImmutableList<ActorUi>,
    ) : NewGroupStatus

    data object NoResults : NewGroupStatus

    // Retryable; screen owns the copy (no localized string in the VM).
    data object Error : NewGroupStatus
}

sealed interface NewGroupEvent : UiEvent {
    /** Tap a search/recent row: add to [selected] (if room), or remove if already picked. */
    data class RecipientToggled(
        val did: String,
    ) : NewGroupEvent

    /** Remove a picked chip. */
    data class RecipientRemoved(
        val did: String,
    ) : NewGroupEvent

    data object CreateTapped : NewGroupEvent

    data object RetryClicked : NewGroupEvent
}

sealed interface NewGroupEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : NewGroupEffect

    data class GroupCreated(
        val convoId: String,
    ) : NewGroupEffect
}

internal const val GROUP_NAME_MAX_GRAPHEMES = 128
internal const val GROUP_NAME_COUNTER_THRESHOLD = 103 // 80% of 128

// The lexicon caps the name at 1280 UTF-8 bytes in addition to 128 graphemes; a name
// under the grapheme cap can still exceed this (e.g. a ZWJ emoji ≈ 25 bytes each).
internal const val GROUP_NAME_MAX_BYTES = 1280
