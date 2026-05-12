package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.profile.impl.R
import net.kikin.nubecita.feature.profile.impl.TabItemUi

private val MEDIA_CELL_GUTTER = 2.dp
private val MEDIA_CELL_CORNER_RADIUS = 2.dp

/**
 * Single cell of the Profile Media grid. Renders the [cell]'s thumb URL
 * via Coil with a square (1:1) aspect ratio — the caller is expected to
 * apply `Modifier.weight(1f).aspectRatio(1f)` so the cell sizes itself
 * to one third of the row width.
 *
 * Tap dispatches via [onClick]; the screen-level effect collector
 * routes this through `ProfileEffect.NavigateToPost(cell.postUri)`.
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
    }
}
