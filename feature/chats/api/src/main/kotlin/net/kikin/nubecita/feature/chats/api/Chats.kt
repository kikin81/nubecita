package net.kikin.nubecita.feature.chats.api

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 destination key for the Chats top-level tab.
 *
 * Lives in `:feature:chats:api` so cross-feature modules that need to push
 * `Chats` onto the back stack can depend on `:feature:chats:api` alone —
 * never on `:feature:chats:impl` (which does not exist yet; until the
 * chats feature epic lands, `:app` registers a placeholder Composable for
 * this key). The key carries no arguments today; if it grows arguments
 * later, the consumer-side `hiltViewModel()` call site will need to switch
 * to assisted injection.
 */
@Serializable
data object Chats : NavKey

/**
 * Per-conversation thread destination. Pushed onto [MainShellNavState] when the user
 * opens a DM or group chat.
 *
 * Carries EITHER a [convoId] OR an [otherUserDid] — at least one must be non-null
 * (enforced by the `init` block). The two entry points differ because a group
 * conversation has no single "other user":
 *  - The convo list pushes [convoId] (it already knows the resolved conversation id,
 *    and group convos can only be identified this way).
 *  - Profile / search (1:1 DM start, future nubecita-a7a) push [otherUserDid]; the
 *    ViewModel resolves it → convoId itself via `chat.bsky.convo.getConvoForMembers`.
 *
 * The ViewModel normalizes the two cases internally, so callers pass whichever
 * identifier they have.
 */
@Serializable
data class Chat(
    val otherUserDid: String? = null,
    val convoId: String? = null,
) : NavKey {
    init {
        require(otherUserDid != null || convoId != null) { "Chat needs convoId or otherUserDid" }
    }
}

/**
 * Recipient picker for starting a new DM. A `@MainShell` sub-route pushed
 * onto the Chats tab by the New-Chat FAB. Selecting a recipient replaces
 * this route with [Chat] (see `MainShellNavState.replaceTop`).
 */
@Serializable
data object NewChat : NavKey

/**
 * Group-creation flow: a recipient-chip picker + group-name field that creates a new
 * group conversation. A `@MainShell` sub-route pushed from the inbox FAB menu's
 * "New group" action. Plain full-screen (mirrors [NewChat]); on success it `replaceTop`s
 * the route with the new [Chat]. Carries no arguments.
 */
@Serializable
data object NewGroup : NavKey

/**
 * Group details / settings for a group conversation (member roster, role/admin,
 * invite link, leave/mute/report). A `@MainShell` sub-route pushed from the chat
 * thread's overflow (⋮) menu; tagged `adaptiveDialog()` so it renders full-screen
 * on Compact and as a centered Dialog on Medium / Expanded. Carries the [convoId]
 * whose membership/details it shows.
 */
@Serializable
data class GroupDetails(
    val convoId: String,
) : NavKey

/**
 * Recipient picker for adding members to a group conversation. A `@MainShell`
 * sub-route pushed from the group-details "Add members" action; tagged
 * `adaptiveDialog()` so it renders full-screen on Compact and as a centered
 * Dialog on Medium / Expanded. Carries the [convoId] whose membership it extends.
 */
@Serializable
data class AddGroupMembers(
    val convoId: String,
) : NavKey

/**
 * Owner-side join-requests list for a group conversation. A `@MainShell` sub-route pushed from
 * the group-details "Join requests" row; tagged `adaptiveDialog()` so it renders full-screen on
 * Compact and as a centered Dialog on Medium / Expanded. Carries the [convoId] whose pending
 * requests it shows.
 */
@Serializable
data class GroupJoinRequests(
    val convoId: String,
) : NavKey

/**
 * Owner-only sub-route to manage the group's single join link (create / share / enable /
 * disable / configure). Pushed from the "Invite link" row in [GroupDetails]. Rendered as an
 * adaptiveDialog (full-screen on Compact, centered dialog on Medium/Expanded).
 */
@Serializable
data class ManageJoinLink(
    val convoId: String,
) : NavKey

/**
 * Deep-link landing for a group invite (`https://nubecita.app/group/join/{code}`). Previews the
 * group via `getGroupPublicInfo` and lets the user join / request to join. Rendered as an
 * adaptiveDialog (full-screen on Compact, centered dialog on Medium/Expanded).
 */
@Serializable
data class GroupJoinPreview(
    val code: String,
) : NavKey

/**
 * Account-global chat settings ("Who can message you"). A `@MainShell`
 * sub-route pushed from the inbox toolbar's gear; tagged `adaptiveDialog()` so
 * it renders full-screen on Compact and as a centered Dialog on Medium /
 * Expanded. Carries no arguments — the `allowIncoming` declaration it edits is
 * a single account-wide preference, not per-conversation.
 */
@Serializable
data object ChatSettings : NavKey
