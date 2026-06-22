package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import kotlin.time.Instant

/** A pending group join request, projected for the join-requests screen. */
@Immutable
data class JoinRequestUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val requestedAt: Instant,
)

@Immutable
data class GroupJoinRequestsViewState(
    val inFlightDids: ImmutableSet<String> = persistentSetOf(),
) : UiState

sealed interface GroupJoinRequestsEvent : UiEvent {
    data class ApproveTapped(
        val did: String,
    ) : GroupJoinRequestsEvent

    data class RejectTapped(
        val did: String,
    ) : GroupJoinRequestsEvent
}

sealed interface GroupJoinRequestsEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : GroupJoinRequestsEffect

    data object RosterChanged : GroupJoinRequestsEffect
}
