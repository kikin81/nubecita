package net.kikin.nubecita.feature.feed.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.feed.impl.R

/**
 * Dismissible "Trending Videos" carousel for the Discover feed — a horizontal
 * strip of portrait posters. Tapping one opens the full-screen vertical video
 * feed at that index. Renders nothing when empty or dismissed.
 *
 * @param onOpen invoked with the tapped video's index (a `VideoFeed(startIndex)`).
 */
@Composable
internal fun TrendingVideosCarousel(
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrendingVideosViewModel = hiltViewModel(),
) {
    val thumbs by viewModel.thumbs.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf(false) }

    if (dismissed || thumbs.isEmpty()) return

    Column(modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.feed_trending_videos_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { dismissed = true }) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.feed_trending_videos_dismiss),
                )
            }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(thumbs, key = { it.index }) { thumb ->
                NubecitaAsyncImage(
                    model = thumb.posterUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .height(160.dp)
                            .aspectRatio(0.5625f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onOpen(thumb.index) },
                )
            }
        }
    }
}
