package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociated
import io.github.kikin81.atproto.app.bsky.actor.ViewerState

/**
 * Resolves whether the signed-in viewer is allowed to send this actor a DM,
 * from the common `(associated, viewer)` shape carried by every `ProfileView*`.
 *
 * Reads `associated.chat.allowIncoming`:
 * - `"none"` → nobody accepts DMs → false.
 * - `"following"` → only accounts the actor follows; encoded viewer-side as
 *   `viewer.followedBy != null` (the actor's follow record points at the viewer).
 * - `"all"` / absent `associated.chat` → true (fail-open, matches official clients).
 *
 * This is a *hint* derived from the search/profile response, not an authoritative
 * check; the post-tap `getConvoForMembers` error remains the source of truth.
 */
fun canViewerMessage(
    associated: ProfileAssociated?,
    viewer: ViewerState?,
): Boolean =
    when (associated?.chat?.allowIncoming) {
        ALLOW_INCOMING_NONE -> false
        ALLOW_INCOMING_FOLLOWING -> viewer?.followedBy != null
        else -> true
    }

private const val ALLOW_INCOMING_NONE = "none"
private const val ALLOW_INCOMING_FOLLOWING = "following"
