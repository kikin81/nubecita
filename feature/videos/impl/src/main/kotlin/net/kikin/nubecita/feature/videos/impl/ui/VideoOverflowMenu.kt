package net.kikin.nubecita.feature.videos.impl.ui

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction

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
