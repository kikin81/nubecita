package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinLinkView
import net.kikin.nubecita.feature.chats.impl.JoinLinkUi
import net.kikin.nubecita.feature.chats.impl.JoinRule
import kotlin.time.Instant

private const val JOIN_LINK_BASE_URL = "https://nubecita.app/group/join"

// chat.bsky.group.defs#linkEnabledStatus sentinel for an active link.
private const val LINK_STATUS_ENABLED = "enabled"

// chat.bsky.group.defs#joinRule known values.
private const val JOIN_RULE_ANYONE = "anyone"
private const val JOIN_RULE_FOLLOWED_BY_OWNER = "followedByOwner"

/**
 * Wire → UI for a group join link.
 *
 * `joinRule` mapping is **fail-closed**: any value other than the two known rules becomes
 * [JoinRule.Unsupported] (never the permissive [JoinRule.Anyone]) so an older build can't silently
 * widen a group when it re-saves a link created by a newer client. [JoinRule.Unsupported] has no
 * wire form (see [toWire]) and the UI locks editing while it is active.
 */
internal fun JoinLinkView.toJoinLinkUi(): JoinLinkUi =
    JoinLinkUi(
        code = code,
        url = "$JOIN_LINK_BASE_URL/$code",
        enabled = enabledStatus == LINK_STATUS_ENABLED,
        joinRule =
            when (joinRule) {
                JOIN_RULE_ANYONE -> JoinRule.Anyone
                JOIN_RULE_FOLLOWED_BY_OWNER -> JoinRule.FollowedByOwner
                else -> JoinRule.Unsupported
            },
        requireApproval = requireApproval,
        createdAt = Instant.parse(createdAt.raw),
    )

/**
 * UI → wire for the editable rules. [JoinRule.Unsupported] has no wire representation and must
 * never reach this function — the UI + ViewModel both gate it out (an unsupported link is read-only).
 */
internal fun JoinRule.toWire(): String =
    when (this) {
        JoinRule.Anyone -> JOIN_RULE_ANYONE
        JoinRule.FollowedByOwner -> JOIN_RULE_FOLLOWED_BY_OWNER
        JoinRule.Unsupported -> error("JoinRule.Unsupported has no wire form")
    }
