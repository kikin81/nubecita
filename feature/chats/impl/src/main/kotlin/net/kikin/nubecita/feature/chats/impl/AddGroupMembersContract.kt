package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

/**
 * A picked recipient shown as a removable chip above the search results. Carries
 * just enough of the [ActorUi] to render the chip and submit the add — the did
 * is what `addMembers` ultimately sends.
 */
@Immutable
data class RecipientUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
)

/**
 * MVI state for the add-group-members recipient picker.
 *
 * [selected] is the picked set rendered as chips; [atCapacity] gates further
 * selection once `existingMembers + selected` reaches [GROUP_MAX_MEMBERS];
 * [isSubmitting] disables the Add action while the `addMembers` call is in
 * flight. The search/recent lifecycle lives in the sealed [AddMembersStatus].
 */
@Immutable
data class AddGroupMembersViewState(
    val selected: ImmutableList<RecipientUi> = persistentListOf(),
    val atCapacity: Boolean = false,
    val isSubmitting: Boolean = false,
    val status: AddMembersStatus = AddMembersStatus.Recent(persistentListOf()),
) : UiState

sealed interface AddMembersStatus {
    data class Recent(
        val items: ImmutableList<ActorUi>,
    ) : AddMembersStatus

    data object Searching : AddMembersStatus

    data class Results(
        val items: ImmutableList<ActorUi>,
    ) : AddMembersStatus

    data object NoResults : AddMembersStatus

    // Retryable; screen owns the copy (no localized string in the VM).
    data object Error : AddMembersStatus
}

sealed interface AddMembersEvent : UiEvent {
    /** Tap a search/recent row: add to [selected] (if room), or remove if already picked. */
    data class RecipientToggled(
        val did: String,
    ) : AddMembersEvent

    /** Remove a picked chip. */
    data class RecipientRemoved(
        val did: String,
    ) : AddMembersEvent

    data object AddTapped : AddMembersEvent

    data object RetryClicked : AddMembersEvent
}

sealed interface AddMembersEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : AddMembersEffect

    data object MembersAdded : AddMembersEffect
}
