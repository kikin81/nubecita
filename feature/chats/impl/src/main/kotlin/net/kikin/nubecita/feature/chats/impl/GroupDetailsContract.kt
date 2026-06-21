package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * A single group-roster member as projected for the group-details screen.
 *
 * Mapped from `chat.bsky.actor.ProfileViewBasic` by `toGroupMemberUi` (the only
 * file allowed to touch the `chat.bsky.actor` member-kind types). The screen/VM
 * that consume this land in later tasks.
 */
@Immutable
data class GroupMemberUi(
    val did: String,
    val handle: String,
    // null → UI renders the handle instead of a display name.
    val displayName: String?,
    val avatarUrl: String?,
    val role: GroupRole,
    // addedBy.displayName ?: addedBy.handle; null when the member joined via link.
    val addedByName: String?,
    // The signed-in user — UI hides the Follow button on self.
    val isViewer: Boolean,
    val followState: FollowState,
    // viewer.following at-uri (for unfollow); null when not following.
    val followUri: String?,
)

/** Member role within a group convo. `memberRole` is only `owner` / `standard`; everything non-owner maps to [Member]. */
enum class GroupRole { Owner, Member }

/** Viewer's follow relationship toward a member, including the optimistic in-flight transition. */
enum class FollowState { NotFollowing, Following, InFlight }

/**
 * MVI state for the group-details screen. One sealed [GroupDetailsLoadStatus] sum
 * carries the load lifecycle; [name] / [muted] / [maxMembers] are header-level
 * fields set once the convo resolves (they can legitimately coexist with any
 * status, so they stay flat per the CLAUDE.md flat-fields rule).
 *
 * [muted] is sourced from the convo-list cache entry for this convoId (see
 * `GroupDetailsViewModel.loadMutedFromCache`), defaulting `false` when the inbox
 * was never opened; [ToggleMute][GroupDetailsEvent.ToggleMute] flips it
 * optimistically and persists via `setMuted`.
 */
@Immutable
data class GroupDetailsViewState(
    val name: String = "",
    val muted: Boolean = false,
    val maxMembers: Int = GROUP_MAX_MEMBERS,
    val status: GroupDetailsLoadStatus = GroupDetailsLoadStatus.Loading,
) : UiState

sealed interface GroupDetailsLoadStatus {
    data object Loading : GroupDetailsLoadStatus

    data class Loaded(
        val members: ImmutableList<GroupMemberUi> = persistentListOf(),
        val memberCount: Int = 0,
    ) : GroupDetailsLoadStatus

    /** Reuses the thread-load [ChatError] taxonomy via `toChatError`. */
    data class InitialError(
        val error: ChatError,
    ) : GroupDetailsLoadStatus
}

sealed interface GroupDetailsEvent : UiEvent {
    data object Refresh : GroupDetailsEvent

    data object RetryClicked : GroupDetailsEvent

    data object BackPressed : GroupDetailsEvent

    /** Per-member optimistic follow/unfollow toggle, keyed by member DID. */
    data class ToggleFollow(
        val did: String,
    ) : GroupDetailsEvent

    data object LeaveTapped : GroupDetailsEvent

    data object ToggleMute : GroupDetailsEvent

    /** Tapping a roster row → open that member's Profile. */
    data class MemberTapped(
        val did: String,
    ) : GroupDetailsEvent
}

sealed interface GroupDetailsEffect : UiEffect {
    data class ShowError(
        val error: ChatError,
    ) : GroupDetailsEffect

    data object NavigateBack : GroupDetailsEffect

    /** Member → Profile navigation; the screen forwards [key] to the MainShell nav state. */
    data class NavigateTo(
        val key: NavKey,
    ) : GroupDetailsEffect
}

/** Bluesky caps group convos at 50 members; drives the "N / 50" roster header. */
internal const val GROUP_MAX_MEMBERS = 50
