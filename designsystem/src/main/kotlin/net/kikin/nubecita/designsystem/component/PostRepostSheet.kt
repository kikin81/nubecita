package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/** Test tag on the repost sheet; absent from composition while it is closed. */
const val POST_REPOST_SHEET_TAG: String = "postRepostSheet"

/** What the viewer picked from [PostRepostSheet]. */
enum class PostRepostAction {
    /** Repost, or undo a repost the viewer already made. */
    Repost,

    Quote,
}

private val POST_REPOST_ACTIONS: ImmutableList<PostRepostAction> =
    persistentListOf(PostRepostAction.Repost, PostRepostAction.Quote)

/**
 * The repost choice: a modal bottom sheet offering Repost and Quote, in the same
 * shape as [PostOverflowSheet] and the official client.
 *
 * Callers compose this **only while the sheet is open**, for the same reason as
 * [PostOverflowSheet]: every post card has a repost cell, so a closed one must
 * cost nothing on a 120 Hz scroll path.
 *
 * Tapping a row hides the sheet, then calls [onDismiss], then [onAction] —
 * exactly once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostRepostSheet(
    isReposted: Boolean,
    onDismiss: () -> Unit,
    onAction: (PostRepostAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // One selection per open sheet: rows stay tappable during the hide
    // animation, and a second tap must not repost twice.
    var selected by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag(POST_REPOST_SHEET_TAG),
    ) {
        PostRepostSheetContent(
            isReposted = isReposted,
            onAction = { action ->
                if (!selected) {
                    selected = true
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
 * The sheet's body — split out of [PostRepostSheet] so screenshot tests can
 * render the rows without a dialog window.
 */
@Composable
fun PostRepostSheetContent(
    isReposted: Boolean,
    onAction: (PostRepostAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    NubecitaListGroup(
        items = POST_REPOST_ACTIONS,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) { action, shapes ->
        NubecitaListItem(
            shapes = shapes,
            headlineContent = { Text(postRepostLabel(action, isReposted)) },
            onClick = { onAction(action) },
            trailingContent = { NubecitaIcon(name = action.iconName(), contentDescription = null) },
        )
    }
}

@Composable
private fun postRepostLabel(
    action: PostRepostAction,
    isReposted: Boolean,
): String =
    when (action) {
        PostRepostAction.Repost ->
            stringResource(if (isReposted) R.string.postcard_action_undo_repost else R.string.postcard_action_repost)
        PostRepostAction.Quote -> stringResource(R.string.postcard_action_quote)
    }

private fun PostRepostAction.iconName(): NubecitaIconName =
    when (this) {
        PostRepostAction.Repost -> NubecitaIconName.Repeat
        PostRepostAction.Quote -> NubecitaIconName.FormatQuote
    }
