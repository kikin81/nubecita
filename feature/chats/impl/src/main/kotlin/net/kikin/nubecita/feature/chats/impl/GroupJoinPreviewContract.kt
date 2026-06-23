package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/** Public preview of a group, shown before joining via an invite link. */
@Immutable
data class GroupPublicInfoUi(
    val name: String,
    val memberCount: Int,
    val ownerDisplayName: String?,
    val ownerHandle: String,
    val ownerAvatarUrl: String?,
    val requireApproval: Boolean,
)

/** Outcome of a `requestJoin` call. */
sealed interface JoinResult {
    /** Joined immediately; [convoId] opens the group thread. */
    data class Joined(
        val convoId: String,
    ) : JoinResult

    /** Request is pending owner approval (the group requires approval). */
    data object Pending : JoinResult
}

/** Mutually-exclusive lifecycle for the group-join preview screen. */
sealed interface GroupJoinPreviewStatus {
    data object Loading : GroupJoinPreviewStatus

    data class Loaded(
        val info: GroupPublicInfoUi,
    ) : GroupJoinPreviewStatus

    data class Error(
        val error: ChatError,
    ) : GroupJoinPreviewStatus

    /** Pending-approval confirmation, shown after a `requestJoin` that returned [JoinResult.Pending]. */
    data object RequestSent : GroupJoinPreviewStatus
}

@Immutable
data class GroupJoinPreviewViewState(
    val status: GroupJoinPreviewStatus = GroupJoinPreviewStatus.Loading,
    val joinInFlight: Boolean = false,
) : UiState

sealed interface GroupJoinPreviewEvent : UiEvent {
    data object Retry : GroupJoinPreviewEvent

    data object JoinTapped : GroupJoinPreviewEvent
}

sealed interface GroupJoinPreviewEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : GroupJoinPreviewEffect

    data class NavigateToConvo(
        val convoId: String,
    ) : GroupJoinPreviewEffect
}
