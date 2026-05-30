package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

data class NewChatState(
    val status: NewChatStatus = NewChatStatus.Recent(persistentListOf()),
) : UiState

sealed interface NewChatStatus {
    data class Recent(
        val items: ImmutableList<ActorUi>,
    ) : NewChatStatus

    data object Searching : NewChatStatus

    data class Results(
        val items: ImmutableList<ActorUi>,
    ) : NewChatStatus

    data object NoResults : NewChatStatus

    // Retryable; screen owns the copy (no localized string in the VM).
    data object Error : NewChatStatus
}

sealed interface NewChatEvent : UiEvent {
    data class RecipientSelected(
        val otherUserDid: String,
    ) : NewChatEvent

    data object RetryClicked : NewChatEvent
}

sealed interface NewChatEffect : UiEffect {
    data class OpenChat(
        val otherUserDid: String,
    ) : NewChatEffect
}
