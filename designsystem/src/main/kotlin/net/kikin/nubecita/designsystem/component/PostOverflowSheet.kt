package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/** Test tag on the post overflow sheet; absent from composition while it is closed. */
const val POST_OVERFLOW_SHEET_TAG: String = "postOverflowSheet"

/**
 * The post overflow menu: a modal bottom sheet of rounded group cards, each row
 * a label on the left and an icon on the right — the official client's post-menu
 * shape. Rows come from [postOverflowGroups], so every surface that shows this
 * sheet (PostCard, the vertical video page) offers the same actions.
 *
 * Callers compose this **only while the sheet is open** (`if (open) PostOverflowSheet(...)`):
 * a closed overflow must cost nothing on a 120 Hz scroll path, so no sheet state
 * exists until the user taps "…".
 *
 * Tapping a row hides the sheet, then calls [onDismiss], then [onAction] —
 * exactly once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostOverflowSheet(
    post: PostUi,
    onDismiss: () -> Unit,
    onAction: (PostOverflowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val groups = remember(post) { postOverflowGroups(post) }
    // One selection per open sheet: rows stay tappable during the hide
    // animation, and a second tap must not dispatch a second action.
    var selected by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(POST_OVERFLOW_SHEET_TAG),
    ) {
        PostOverflowGroupsContent(
            groups = groups,
            handle = post.author.handle,
            onAction = { action ->
                if (!selected) {
                    selected = true
                    // Sequential, on the composition's main-thread scope: dispatch
                    // only after the hide animation completes.
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onAction(action)
                    }
                }
            },
        )
    }
}

/**
 * The sheet's body — split out of [PostOverflowSheet] so screenshot tests can
 * render the grouped rows without a dialog window.
 */
@Composable
fun PostOverflowGroupsContent(
    groups: ImmutableList<PostOverflowGroup>,
    handle: String,
    onAction: (PostOverflowAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // Scrollable so every row stays reachable on a short window or with
        // enlarged text (up to eight rows on your own post).
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        groups.forEach { group ->
            NubecitaListGroup(items = group.actions) { action, shapes ->
                NubecitaListItem(
                    shapes = shapes,
                    headlineContent = { Text(postOverflowLabel(action, handle)) },
                    onClick = { onAction(action) },
                    trailingContent = { NubecitaIcon(name = action.iconName(), contentDescription = null) },
                )
            }
        }
    }
}

/** The row label for [action]; `@handle`-bearing rows interpolate [handle]. */
@Composable
fun postOverflowLabel(
    action: PostOverflowAction,
    handle: String,
): String =
    when (action) {
        PostOverflowAction.ReportPost -> stringResource(R.string.moderation_action_report_post)
        PostOverflowAction.ReportAuthor -> stringResource(R.string.moderation_action_report_author, handle)
        PostOverflowAction.MuteAuthor -> stringResource(R.string.moderation_action_mute_author, handle)
        PostOverflowAction.UnmuteAuthor -> stringResource(R.string.moderation_action_unmute_author, handle)
        PostOverflowAction.BlockAuthor -> stringResource(R.string.moderation_action_block_author, handle)
        PostOverflowAction.UnblockAuthor -> stringResource(R.string.moderation_action_unblock_author, handle)
        PostOverflowAction.MuteThread -> stringResource(R.string.moderation_action_mute_thread)
        PostOverflowAction.UnmuteThread -> stringResource(R.string.moderation_action_unmute_thread)
        PostOverflowAction.CopyPostText -> stringResource(R.string.moderation_action_copy_post_text)
        PostOverflowAction.TranslatePost -> stringResource(R.string.moderation_action_translate_post)
        PostOverflowAction.DeletePost -> stringResource(R.string.postcard_action_delete_post)
        PostOverflowAction.PinPost -> stringResource(R.string.post_overflow_pin)
        PostOverflowAction.UnpinPost -> stringResource(R.string.post_overflow_unpin)
    }

/** The trailing icon for [this] row. */
fun PostOverflowAction.iconName(): NubecitaIconName =
    when (this) {
        PostOverflowAction.TranslatePost -> NubecitaIconName.Translate
        PostOverflowAction.CopyPostText -> NubecitaIconName.ContentCopy
        PostOverflowAction.MuteThread -> NubecitaIconName.NotificationsOff
        PostOverflowAction.UnmuteThread -> NubecitaIconName.Notifications
        PostOverflowAction.MuteAuthor -> NubecitaIconName.VolumeOff
        PostOverflowAction.UnmuteAuthor -> NubecitaIconName.VolumeUp
        PostOverflowAction.BlockAuthor, PostOverflowAction.UnblockAuthor -> NubecitaIconName.Block
        PostOverflowAction.ReportPost, PostOverflowAction.ReportAuthor -> NubecitaIconName.Flag
        PostOverflowAction.DeletePost -> NubecitaIconName.Delete
        PostOverflowAction.PinPost, PostOverflowAction.UnpinPost -> NubecitaIconName.Keep
    }
