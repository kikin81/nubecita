package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.Chat

/**
 * Derive the open conversation's `convoId` from the MainShell back stack — the
 * topmost [Chat] thread's convoId, or `null` when none is open (or the open
 * thread was started by `otherUserDid`, e.g. a profile-initiated DM, whose convoId
 * the screen resolves only after navigating). Feeds the list pane's selected-row
 * highlight in the tablet list-detail layout. The `Chats` entry reads
 * `LocalMainShellNavState.current.backStack` (a SnapshotStateList) so this
 * recomposes on navigation; on Compact the list pane isn't composed while a
 * thread is open, so a non-null result there is simply never painted.
 */
internal fun selectedConvoId(backStack: List<NavKey>): String? = (backStack.lastOrNull { it is Chat } as? Chat)?.convoId

/**
 * Derive the open conversation's `otherUserDid` from the MainShell back stack — the
 * topmost [Chat] thread's otherUserDid, or `null` when none is open or the thread
 * was opened by convoId (the convo-list path). This is the fallback highlight key
 * for **profile-initiated DMs**, which push `Chat(otherUserDid = …)` with a null
 * convoId: [selectedConvoId] can't match them, so the list pane matches the Direct
 * row by did instead. Same recompose/Compact semantics as [selectedConvoId].
 */
internal fun selectedOtherUserDid(backStack: List<NavKey>): String? = (backStack.lastOrNull { it is Chat } as? Chat)?.otherUserDid
