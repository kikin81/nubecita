package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.TabItemUi

private val MEDIA_CELL_GUTTER = 2.dp
private val MEDIA_CELL_CORNER_RADIUS = 2.dp
private val VIDEO_BADGE_SIZE = 28.dp
private val VIDEO_BADGE_INSET = 6.dp
private val VIDEO_BADGE_ICON_SIZE = 18.dp

/**
 * Single cell of the Profile Media grid. Renders the [cell]'s thumb URL
 * via Coil with a square (1:1) aspect ratio — the caller is expected to
 * apply `Modifier.weight(1f).aspectRatio(1f)` so the cell sizes itself
 * to one third of the row width.
 *
 * Tap dispatches via [onClick]; the screen wires it to
 * `ProfileEvent.OnMediaCellTapped(cell.postUri, cell.isVideo)`. The
 * VM branches: image cells emit `ProfileEffect.NavigateToMediaViewer`,
 * video cells emit `ProfileEffect.NavigateToVideoPlayer`. Video cells
 * also render a small PlayArrow badge overlay so users can tell at a
 * glance which thumbs are video.
 *
 * Image URL source: `cell.thumbUrl` is already a thumbnail-sized URL
 * via `nubecita-nwn`'s `thumbOrFullsize()` projection (falls back to
 * fullsize when the source lacks a thumb). `NubecitaAsyncImage`
 * renders a flat `surfaceContainerHighest` ColorPainter placeholder
 * while Coil fetches and on error/fallback.
 *
 * Accessibility:
 * - `contentDescription = null` on the image because a 3-col grid of
 *   nearly-identical media is decorative as a cluster — per-cell image
 *   descriptions would create excessive TalkBack noise.
 * - `Modifier.clickable(role = Role.Button, onClickLabel = ...)` so
 *   TalkBack announces "View post, button. Double tap to activate."
 *   per cell. Matches the codebase pattern in `:designsystem.PostStat`
 *   and `:designsystem.ThreadFold`.
 */
@Composable
internal fun MediaCellThumb(
    cell: TabItemUi.MediaCell,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clickLabel = stringResource(R.string.profile_media_cell_click_label)
    Box(
        modifier =
            modifier
                .padding(MEDIA_CELL_GUTTER)
                .clip(RoundedCornerShape(MEDIA_CELL_CORNER_RADIUS))
                .clickable(
                    role = Role.Button,
                    onClickLabel = clickLabel,
                    onClick = onClick,
                ),
    ) {
        NubecitaAsyncImage(
            model = cell.thumbUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (cell.isVideo) {
            VideoBadge(modifier = Modifier.align(Alignment.BottomEnd))
        }
    }
}

/**
 * Small play-button overlay on a Media-grid thumb that's a video post.
 * Bottom-end corner so it doesn't obscure faces or text centered in
 * the poster frame. The circular black scrim provides contrast over
 * arbitrary thumb backgrounds.
 */
@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(VIDEO_BADGE_INSET)
                .size(VIDEO_BADGE_SIZE)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(VIDEO_BADGE_ICON_SIZE),
        )
    }
}
