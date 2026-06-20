package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.Chat

/**
 * Derive the open conversation's `convoId` from the MainShell back stack — the
 * topmost [Chat] thread's convoId, or `null` when none is open (or the open
 * thread was started by `otherUserDid` and hasn't been re-keyed, e.g. a
 * profile-initiated DM). Feeds the list pane's selected-row highlight in the
 * tablet list-detail layout. The `Chats` entry reads
 * `LocalMainShellNavState.current.backStack` (a SnapshotStateList) so this
 * recomposes on navigation; on Compact the list pane isn't composed while a
 * thread is open, so a non-null result there is simply never painted.
 */
internal fun selectedConvoId(backStack: List<NavKey>): String? = (backStack.lastOrNull { it is Chat } as? Chat)?.convoId
