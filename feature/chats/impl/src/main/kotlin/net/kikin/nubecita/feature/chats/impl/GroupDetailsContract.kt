package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable

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
