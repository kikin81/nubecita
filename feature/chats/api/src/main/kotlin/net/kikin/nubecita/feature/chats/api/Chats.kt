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
 * taps a row in the convo list or (future, nubecita-a7a) the Message button on
 * another user's profile. The screen resolves [otherUserDid] → convoId itself via
 * `chat.bsky.convo.getConvoForMembers`.
 */
@Serializable
data class Chat(
    val otherUserDid: String,
) : NavKey
