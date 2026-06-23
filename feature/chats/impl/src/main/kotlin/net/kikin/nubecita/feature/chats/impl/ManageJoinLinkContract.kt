package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import kotlin.time.Instant

/** How a join link admits new members. Mirrors `chat.bsky.group.defs#joinRule`. */
enum class JoinRule {
    /** Anyone with the link may join / request. Wire value `"anyone"`. */
    Anyone,

    /** Only accounts the owner follows may join / request. Wire value `"followedByOwner"`. */
    FollowedByOwner,

    /**
     * The link carries a rule this build does not understand (a newer client created it).
     * Fail-closed: never sent back to the server and the UI locks editing while it is active.
     */
    Unsupported,
}

/** A group's single join link, projected for the manage-link screen. */
@Immutable
data class JoinLinkUi(
    val code: String,
    val url: String,
    val enabled: Boolean,
    val joinRule: JoinRule,
    val requireApproval: Boolean,
    val createdAt: Instant,
)

/** Mutually-exclusive load lifecycle for the manage-link screen. */
sealed interface ManageJoinLinkStatus {
    data object Loading : ManageJoinLinkStatus

    /** Settled load. [link] is `null` when the group has no join link yet (show the create UI). */
    data class Loaded(
        val link: JoinLinkUi?,
    ) : ManageJoinLinkStatus

    data class Error(
        val error: ChatError,
    ) : ManageJoinLinkStatus
}

@Immutable
data class ManageJoinLinkViewState(
    val status: ManageJoinLinkStatus = ManageJoinLinkStatus.Loading,
    val mutationInFlight: Boolean = false,
) : UiState

sealed interface ManageJoinLinkEvent : UiEvent {
    data object Retry : ManageJoinLinkEvent

    data class CreateTapped(
        val joinRule: JoinRule,
        val requireApproval: Boolean,
    ) : ManageJoinLinkEvent

    data class JoinRuleChanged(
        val joinRule: JoinRule,
    ) : ManageJoinLinkEvent

    data class RequireApprovalChanged(
        val requireApproval: Boolean,
    ) : ManageJoinLinkEvent

    data object EnableTapped : ManageJoinLinkEvent

    data object DisableTapped : ManageJoinLinkEvent
}

sealed interface ManageJoinLinkEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : ManageJoinLinkEffect
}
