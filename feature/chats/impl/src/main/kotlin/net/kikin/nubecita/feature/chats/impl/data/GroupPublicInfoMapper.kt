package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.GroupPublicView
import net.kikin.nubecita.feature.chats.impl.GroupPublicInfoUi

/** Wire → UI for an invite-link group preview. */
internal fun GroupPublicView.toGroupPublicInfoUi(): GroupPublicInfoUi =
    GroupPublicInfoUi(
        name = name,
        memberCount = memberCount.toInt(),
        ownerDisplayName = owner.displayName?.takeUnless { it.isBlank() },
        ownerHandle = owner.handle.raw,
        ownerAvatarUrl = owner.avatar?.raw,
        requireApproval = requireApproval,
    )
