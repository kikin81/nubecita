package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinRequestView
import net.kikin.nubecita.feature.chats.impl.JoinRequestUi
import kotlin.time.Instant

/** `chat.bsky.group.JoinRequestView` → UI [JoinRequestUi] (requester profile + requested time). */
internal fun JoinRequestView.toJoinRequestUi(): JoinRequestUi =
    JoinRequestUi(
        did = requestedBy.did.raw,
        handle = requestedBy.handle.raw,
        displayName = requestedBy.displayName?.takeUnless { it.isBlank() },
        avatarUrl = requestedBy.avatar?.raw,
        requestedAt = Instant.parse(requestedAt.raw),
    )
