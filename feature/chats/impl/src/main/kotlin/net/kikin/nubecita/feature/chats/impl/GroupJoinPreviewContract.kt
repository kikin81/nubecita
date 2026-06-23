package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable

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
