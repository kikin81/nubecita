package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
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
