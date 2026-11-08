package net.kikin.nubecita.wear.feed

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OpenOnPhoneDialog
import androidx.wear.compose.material3.OpenOnPhoneDialogDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.openOnPhoneDialogCurvedText
import coil3.compose.rememberAsyncImagePainter
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.ui.WearScrollIndicator
import net.kikin.nubecita.wear.ui.roundEdgeFade
import net.kikin.nubecita.wear.ui.roundTextInset
import java.io.File

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
    val transformationSpec = rememberTransformationSpec()
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
            item {
                val thumbnailPath = post.thumbnailPath
                if (thumbnailPath != null) {
                    PostImage(
                        path = thumbnailPath,
                        description = stringResource(if (post.isVideo) R.string.wear_post_video else R.string.wear_post_image),
                        transformation = SurfaceTransformation(transformationSpec),
                        modifier =
                            Modifier
                                .padding(horizontal = textInset)
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec)
                                .minimumVerticalContentPadding(CardDefaults.minimumVerticalListContentPadding)
                                .height(POST_IMAGE_HEIGHT_DP.dp),
                    )
                } else {
                    PostMedia(post, imageHeight = POST_IMAGE_HEIGHT_DP, modifier = Modifier.padding(horizontal = textInset))
                }
            }
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

/**
 * The post's image as a card background. A plain image keeps its square corners
 * and gets cut off near the top and bottom of a round screen; a card surface
 * narrows as it reaches them. No scrim: nothing is drawn over the image.
 */
@Composable
private fun PostImage(
    path: String,
    description: String,
    transformation: SurfaceTransformation,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.semantics { contentDescription = description },
        containerPainter =
            CardDefaults.containerPainter(
                image = rememberAsyncImagePainter(File(path)),
                scrim = SolidColor(Color.Transparent),
                // Crops to the card; the default stretches the image to fit it.
                sizeToIntrinsics = true,
                contentScale = ContentScale.Crop,
            ),
        transformation = transformation,
    ) {}
}

private const val POST_IMAGE_HEIGHT_DP = 140

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
