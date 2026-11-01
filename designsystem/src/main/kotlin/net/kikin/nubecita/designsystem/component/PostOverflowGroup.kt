package net.kikin.nubecita.designsystem.component

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.PostUi

/** One rounded card of rows in the post overflow sheet. */
@Immutable
data class PostOverflowGroup(
    val actions: ImmutableList<PostOverflowAction>,
)

/**
 * The post overflow sheet's rows for [post], grouped into cards in display
 * order. Shared by `PostCard` and the vertical video page so the two surfaces
 * can never drift apart; pure so the rules are unit-tested without Compose.
 *
 * Order follows the official client's post menu (social-app `PostMenuItems.tsx`):
 * - Own post: Pin / Unpin → utility → thread → Delete. Report / Mute / Block aimed at
 *   yourself are omitted.
 * - Others' post: utility → thread → moderation (Mute, Block, Report last).
 *
 * Translate is omitted on a post with no text. Each toggle pair renders exactly
 * one row, keyed on its viewer flag. Empty groups are dropped.
 */
fun postOverflowGroups(post: PostUi): ImmutableList<PostOverflowGroup> {
    val viewer = post.viewer
    // Own posts lead with Pin / Unpin. Eligibility is authorship alone —
    // replies and quotes included — matching social-app's PostMenuItems.
    val pin =
        if (viewer.isOwnPost) {
            listOf(if (viewer.isPinned) PostOverflowAction.UnpinPost else PostOverflowAction.PinPost)
        } else {
            emptyList()
        }
    val utility =
        buildList {
            if (post.text.isNotBlank()) add(PostOverflowAction.TranslatePost)
            add(PostOverflowAction.CopyPostText)
        }
    val thread =
        listOf(if (viewer.isThreadMuted) PostOverflowAction.UnmuteThread else PostOverflowAction.MuteThread)
    val last =
        if (viewer.isOwnPost) {
            listOf(PostOverflowAction.DeletePost)
        } else {
            listOf(
                if (viewer.isAuthorMutedByViewer) PostOverflowAction.UnmuteAuthor else PostOverflowAction.MuteAuthor,
                if (viewer.isAuthorBlockedByViewer) PostOverflowAction.UnblockAuthor else PostOverflowAction.BlockAuthor,
                PostOverflowAction.ReportPost,
                PostOverflowAction.ReportAuthor,
            )
        }
    return listOf(pin, utility, thread, last)
        .filter { it.isNotEmpty() }
        .map { PostOverflowGroup(it.toImmutableList()) }
        .toImmutableList()
}
