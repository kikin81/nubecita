package net.kikin.nubecita.wear.feed

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.ui.WearScrollIndicator
import net.kikin.nubecita.wear.ui.roundEdgeFade
import net.kikin.nubecita.wear.ui.roundTextInset

/**
 * One post in full: the whole text and a larger image. The author is a centered
 * header because the top of a round screen is too narrow for a left-aligned
 * row. Text and media take [roundTextInset], since the list's own padding leaves
 * them against the edge, and the list fades at the ends. Plain text items take
 * no `transformedHeight` (that edge morph is for surfaces and crops text).
 */
@Composable
fun PostScreen(
    post: WatchPostUi,
    nowMs: Long,
    modifier: Modifier = Modifier,
    actionsAvailable: Boolean = false,
    onToggleLike: () -> Unit = {},
    onToggleRepost: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onOpenOnPhone: (() -> Unit)? = null,
    listState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
) {
    var showOpenOnPhone by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(
        scrollState = listState,
        modifier = modifier,
        scrollIndicator = { WearScrollIndicator(listState) },
        edgeButton = {
            if (onOpenOnPhone != null) {
                EdgeButton(
                    onClick = {
                        onOpenOnPhone()
                        showOpenOnPhone = true
                    },
                    buttonSize = EdgeButtonSize.Small,
                    // Lets a drag on the button scroll the list, with overscroll.
                    modifier =
                        Modifier.scrollable(
                            listState,
                            orientation = Orientation.Vertical,
                            reverseDirection = true,
                            overscrollEffect = rememberOverscrollEffect(),
                        ),
                ) {
                    Text(stringResource(R.string.wear_gate_open_on_phone))
                }
            }
        },
    ) { contentPadding ->
        val textInset = roundTextInset()
        TransformingLazyColumn(state = listState, modifier = Modifier.roundEdgeFade(listState), contentPadding = contentPadding) {
            item { CenteredAuthor(post, nowMs) }
            post.repostedBy?.let { by ->
                item {
                    Text(
                        stringResource(R.string.wear_post_reposted_by, by),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = textInset),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (post.text.isNotBlank()) {
                item { Text(post.text, modifier = Modifier.fillMaxWidth().padding(horizontal = textInset), style = MaterialTheme.typography.bodyMedium) }
            }
            item { PostMedia(post, imageHeight = 140, modifier = Modifier.padding(horizontal = textInset)) }
            item { PostCounts(post) }
            // Hidden for a phone that doesn't run watch actions yet.
            if (actionsAvailable) {
                item { PostActionRow(post, onToggleLike = onToggleLike, onToggleRepost = onToggleRepost, onToggleBookmark = onToggleBookmark) }
            }
        }
    }
    val text = OpenOnPhoneDialogDefaults.text
    val style = OpenOnPhoneDialogDefaults.curvedTextStyle
    OpenOnPhoneDialog(
        visible = showOpenOnPhone,
        onDismissRequest = { showOpenOnPhone = false },
        curvedText = { openOnPhoneDialogCurvedText(text = text, style = style) },
    )
}

/** Avatar, name and time stacked and centered: fits the narrow top of a round screen. */
@Composable
private fun CenteredAuthor(
    post: WatchPostUi,
    nowMs: Long,
) {
    // Inset so a long name ellipsizes inside the circle, not past its edge.
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = roundTextInset()), horizontalAlignment = Alignment.CenterHorizontally) {
        AuthorAvatar(post.avatarPath)
        Text(post.authorName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            relativeTime(post.createdAtMs, nowMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
