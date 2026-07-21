package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.designsystem.R as DesignSystemR

/**
 * The overflow items to show for [viewer], in display order. Mirrors PostCard's
 * closed-menu rules: report always; exactly one of mute/unmute and block/unblock
 * keyed on the viewer flags; mute-thread and copy always. `UnmuteThread` is a
 * type-surface-only variant and never appears here.
 *
 * Pure so the mute-vs-unmute / block-vs-unblock branching is unit-tested without
 * rendering a DropdownMenu, which layoutlib can't compose deterministically.
 */
internal fun videoOverflowActions(viewer: ViewerStateUi): ImmutableList<PostOverflowAction> =
    persistentListOf(
        PostOverflowAction.ReportPost,
        if (viewer.isAuthorMutedByViewer) PostOverflowAction.UnmuteAuthor else PostOverflowAction.MuteAuthor,
        if (viewer.isAuthorBlockedByViewer) PostOverflowAction.UnblockAuthor else PostOverflowAction.BlockAuthor,
        PostOverflowAction.MuteThread,
        PostOverflowAction.CopyPostText,
    )

/**
 * The vertical feed's overflow menu. PostCard's `PostOverflowAffordance` is private
 * and coupled to its internal `PostStat`, so this is a videos-local `DropdownMenu`
 * over the same [PostOverflowAction] type. Item strings are the shared
 * `:designsystem` `moderation_action_*` resources.
 */
@Composable
internal fun VideoOverflowMenu(
    post: PostUi,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (PostOverflowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = modifier) {
        videoOverflowActions(post.viewer).forEach { action ->
            DropdownMenuItem(
                text = { Text(overflowActionLabel(action, post.author.handle)) },
                onClick = {
                    onDismiss()
                    onAction(action)
                },
            )
        }
    }
}

@Composable
private fun overflowActionLabel(
    action: PostOverflowAction,
    handle: String,
): String =
    when (action) {
        PostOverflowAction.ReportPost -> stringResource(DesignSystemR.string.moderation_action_report_post)
        PostOverflowAction.MuteAuthor -> stringResource(DesignSystemR.string.moderation_action_mute_author, handle)
        PostOverflowAction.UnmuteAuthor -> stringResource(DesignSystemR.string.moderation_action_unmute_author, handle)
        PostOverflowAction.BlockAuthor -> stringResource(DesignSystemR.string.moderation_action_block_author, handle)
        PostOverflowAction.UnblockAuthor -> stringResource(DesignSystemR.string.moderation_action_unblock_author, handle)
        PostOverflowAction.MuteThread -> stringResource(DesignSystemR.string.moderation_action_mute_thread)
        // Never rendered — videoOverflowActions omits it — but the when must be exhaustive.
        PostOverflowAction.UnmuteThread -> stringResource(DesignSystemR.string.moderation_action_mute_thread)
        PostOverflowAction.CopyPostText -> stringResource(DesignSystemR.string.moderation_action_copy_post_text)
    }
