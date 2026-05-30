package net.kikin.nubecita.core.profile

import io.github.kikin81.atproto.app.bsky.actor.ProfileAssociated
import io.github.kikin81.atproto.app.bsky.actor.ViewerState

/**
 * Resolves whether the signed-in viewer is allowed to send this actor a DM,
 * from the common `(associated, viewer)` shape carried by every `ProfileView*`.
 *
 * Reads `associated.chat.allowIncoming`:
 * - `"all"` → anyone may DM → true.
 * - `"none"` → nobody accepts DMs → false.
 * - `"following"` → only accounts the actor follows; encoded viewer-side as
 *   `viewer.followedBy != null` (the actor's follow record points at the viewer).
 * - **absent** `associated.chat` (or an unrecognized value) → treated as
 *   `"following"`. Bluesky's default "allow messages from" setting is *people
 *   you follow*, and accounts that never customized it omit the `chat` block
 *   entirely. The official client greys these out unless they follow you back,
 *   so we gate them the same way: `viewer.followedBy != null`. This is
 *   deliberately NOT fail-open — assuming "messageable" for an unknown setting
 *   surfaces recipients you can't actually DM (verified against the official app).
 *
 * This is a *hint* derived from the search/profile response, not an authoritative
 * check; the post-tap `getConvoForMembers` error remains the source of truth.
 */
fun canViewerMessage(
    associated: ProfileAssociated?,
    viewer: ViewerState?,
): Boolean =
    when (associated?.chat?.allowIncoming) {
        ALLOW_INCOMING_ALL -> true
        ALLOW_INCOMING_NONE -> false
        else -> viewer?.followedBy != null
    }

private const val ALLOW_INCOMING_ALL = "all"
private const val ALLOW_INCOMING_NONE = "none"
