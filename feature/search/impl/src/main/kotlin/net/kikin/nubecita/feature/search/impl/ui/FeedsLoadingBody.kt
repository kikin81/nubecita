package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.R

/**
 * Loading body for the Feeds tab. Three skeleton feed-row placeholders.
 * Same hand-rolled shape as [PeopleLoadingBody] with the avatar slot
 * swapped for a rounded-square tile to match [FeedRow]'s icon shape.
 */
@Composable
internal fun FeedsLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_feeds_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        repeat(3) {
            FeedRowSkeleton()
        }
    }
}

@Composable
private fun FeedRowSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(placeholderColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = 0.5f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = 0.7f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction = 0.85f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor),
            )
        }
    }
}

@Preview(name = "FeedsLoadingBody — light", showBackground = true)
@Preview(
    name = "FeedsLoadingBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedsLoadingBodyPreview() {
    NubecitaTheme {
        FeedsLoadingBody()
    }
}
