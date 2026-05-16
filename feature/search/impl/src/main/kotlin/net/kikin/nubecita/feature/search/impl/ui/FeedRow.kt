package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi

/**
 * Single-feed row for the Feeds tab.
 *
 * Leading 48dp rounded-square icon — Coil-loaded avatar URL when
 * present, [NubecitaAsyncImage]'s standard flat-tile placeholder
 * otherwise. Primary line: feed display name (semibold). Secondary
 * line: "by @handle" (or "by Display (@handle)" when the creator has a
 * display name). Tertiary line: description capped at 2 lines.
 * Trailing meta line: localized "%d like(s)" — Bluesky surfaces a
 * feed's like count as the de-facto popularity metric.
 *
 * Stateless. Click dispatch is via [onClick]; the parent
 * [FeedsTabContent] wires it to a `SearchFeedsEvent.FeedTapped`. No
 * query-substring highlighting (the Feeds tab doesn't do it — see
 * `SearchFeedsContract`'s KDoc).
 */
@Composable
internal fun FeedRow(
    feed: FeedGeneratorUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        NubecitaAsyncImage(
            model = feed.avatarUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = feed.byline(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (feed.description != null) {
                Text(
                    text = feed.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text =
                    androidx.compose.ui.res.pluralStringResource(
                        id = R.plurals.search_feeds_like_count,
                        count = feed.likeCount.toInt().coerceAtLeast(0),
                        feed.likeCount,
                    ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun FeedGeneratorUi.byline(): String =
    if (creatorDisplayName != null) {
        "by $creatorDisplayName (@$creatorHandle)"
    } else {
        "by @$creatorHandle"
    }

@Preview(name = "FeedRow — light", showBackground = true)
@Composable
private fun FeedRowLightPreview() {
    NubecitaTheme {
        FeedRow(feed = SAMPLE_FEED, onClick = {})
    }
}

@Preview(name = "FeedRow — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedRowDarkPreview() {
    NubecitaTheme {
        FeedRow(feed = SAMPLE_FEED, onClick = {})
    }
}

@Preview(name = "FeedRow — no creator display name", showBackground = true)
@Composable
private fun FeedRowNoCreatorDisplayNamePreview() {
    NubecitaTheme {
        FeedRow(
            feed = SAMPLE_FEED.copy(creatorDisplayName = null, description = null),
            onClick = {},
        )
    }
}

private val SAMPLE_FEED =
    FeedGeneratorUi(
        uri = "at://did:plc:fake/app.bsky.feed.generator/sample",
        displayName = "Discover",
        creatorHandle = "skyfeed.bsky.social",
        creatorDisplayName = "skyfeed",
        description = "A curated feed of trending posts on Bluesky, refreshed every few hours.",
        avatarUrl = null,
        likeCount = 14_237L,
    )
