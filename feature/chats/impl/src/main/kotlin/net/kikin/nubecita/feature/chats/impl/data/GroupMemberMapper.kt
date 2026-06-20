package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.GroupConvoMember
import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import net.kikin.nubecita.feature.chats.impl.FollowState
import net.kikin.nubecita.feature.chats.impl.GroupMemberUi
import net.kikin.nubecita.feature.chats.impl.GroupRole

/**
 * `chat.bsky.actor.ProfileViewBasic` (a group-roster member) → UI [GroupMemberUi].
 *
 * The ONLY file allowed to touch the `chat.bsky.actor` member-kind types
 * ([GroupConvoMember] etc.). The `kind` union carries the group-specific role +
 * inviter; non-group / unknown kind variants are treated as a plain member.
 *
 * `memberRole` known values are only `owner` / `standard` (there is no admin role),
 * so anything that isn't `owner` maps to [GroupRole.Member]. `addedBy` is absent when
 * the member joined via a join link, in which case [GroupMemberUi.addedByName] is null.
 */
internal fun ProfileViewBasic.toGroupMemberUi(viewerDid: String): GroupMemberUi {
    val group = kind as? GroupConvoMember
    val role = if (group?.role == "owner") GroupRole.Owner else GroupRole.Member
    val addedBy = group?.addedBy
    val followUri = viewer?.following?.raw
    return GroupMemberUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeUnless { it.isBlank() },
        avatarUrl = avatar?.raw,
        role = role,
        addedByName = addedBy?.let { it.displayName?.takeUnless { name -> name.isBlank() } ?: it.handle.raw },
        isViewer = did.raw == viewerDid,
        followState = if (followUri != null) FollowState.Following else FollowState.NotFollowing,
        followUri = followUri,
    )
}
