package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.Chat

/**
 * Derive the open conversation's `otherUserDid` from the MainShell back
 * stack — the topmost [Chat] thread, or `null` when none is open.
 *
 * Feeds the list pane's selected-row highlight in the tablet list-detail
 * layout: the `Chats` entry reads `LocalMainShellNavState.current.backStack`
 * (a `SnapshotStateList`, so this recomposes on navigation) and passes the
 * result down to [net.kikin.nubecita.feature.chats.impl.ui.ConvoListItem].
 *
 * On Compact widths the list pane isn't composed while a thread is open
 * (single-pane shows only the detail), so a non-null result there is
 * simply never painted — no width gate needed at the call site.
 */
internal fun selectedConvoDid(backStack: List<NavKey>): String? = (backStack.lastOrNull { it is Chat } as? Chat)?.otherUserDid
