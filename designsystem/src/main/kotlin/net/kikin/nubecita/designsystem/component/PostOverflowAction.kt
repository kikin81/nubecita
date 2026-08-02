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

    /**
     * Hand the post's text to an external translator.
     *
     * The menu omits this row on a post with no text (an image- or
     * video-only post), rather than showing it disabled — there is nothing
     * to translate and the row would be a dead end.
     *
     * Deliberately external for now: it opens a translator in a Custom Tab
     * rather than translating in place. In-place translation, language
     * detection and facet-preserving rendering are a separate epic
     * (nubecita-s6xk); this is the version that was actually asked for.
     */
    data object TranslatePost : PostOverflowAction

    /**
     * Delete the viewer's own post. Emitted only when
     * `PostUi.viewer.isOwnPost` — the menu omits the row entirely on anybody
     * else's post rather than showing it disabled, since a viewer can never
     * delete a post they did not author.
     *
     * Irreversible, so the consuming surface confirms before acting.
     */
    data object DeletePost : PostOverflowAction
}
