package net.kikin.nubecita.designsystem.component

import androidx.compose.runtime.Immutable

/**
 * Closed sum of overflow-menu actions a viewer can take on a post.
 *
 * The variants here are the surface the [PostCard] overflow menu emits
 * to its host VM. Host VMs route each variant through an MVI event to a
 * snackbar / coming-soon effect in oftc.2; the real moderation RPC
 * wiring lands in oftc.3 (Report), oftc.4 (Block / Unblock), and
 * oftc.5 (Mute / Unmute / MuteThread / UnmuteThread).
 *
 * `Author` (Mute / Block) variants come in pairs — the menu renders
 * exactly one of each pair based on `post.viewer.isAuthorMutedByViewer`
 * / `post.viewer.isAuthorBlockedByViewer`. The thread pair is asymmetric
 * in oftc.2: the menu emits only [MuteThread] because per-post
 * thread-mute-state is not yet projected onto `PostUi` (that lands with
 * oftc.5 / oftc.7). [UnmuteThread] is declared so host VMs' `when`
 * switches stay exhaustive ahead of that wiring — it is part of the
 * type surface but not currently emitted by the closed-state menu.
 */
@Immutable
sealed interface PostOverflowAction {
    data object ReportPost : PostOverflowAction

    data object MuteAuthor : PostOverflowAction

    data object UnmuteAuthor : PostOverflowAction

    data object BlockAuthor : PostOverflowAction

    data object UnblockAuthor : PostOverflowAction

    data object MuteThread : PostOverflowAction

    data object UnmuteThread : PostOverflowAction

    data object CopyPostText : PostOverflowAction
}
