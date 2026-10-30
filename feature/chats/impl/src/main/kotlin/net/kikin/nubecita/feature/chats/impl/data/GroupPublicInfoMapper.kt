package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinLinkPreviewView
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi

/**
 * Wire → UI for an invite-link group preview (`chat.bsky.group.defs#joinLinkPreviewView`).
 *
 * `GroupPublicInfoUi` carries no owner DID; downstream avatar fallbacks seed their hue from
 * the handle alone.
 */
internal fun JoinLinkPreviewView.toGroupPublicInfoUi(): GroupPublicInfoUi =
    GroupPublicInfoUi(
        name = name,
        memberCount = memberCount.toInt(),
        ownerDisplayName = owner.displayName?.takeUnless { it.isBlank() },
        ownerHandle = owner.handle.raw,
        ownerAvatarUrl = owner.avatar?.raw,
        requireApproval = requireApproval,
    )

/** The invite-link [code] is disabled or doesn't map to a joinable group. */
internal class InvalidJoinLinkException(
    code: String,
) : Exception("Join link $code is disabled or invalid")
