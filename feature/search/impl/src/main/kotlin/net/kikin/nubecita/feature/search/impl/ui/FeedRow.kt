package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
 * display name) — both variants come from string resources so the row
 * localizes alongside the rest of the Search UI. Tertiary line:
 * description, capped at 2 lines. Trailing meta line: localized
 * "%d like(s)".
 *
 * **Non-interactive in V1.** There is intentionally no `clickable`
 * modifier here. `:feature:feeddetail:api` doesn't exist yet, so a
 * tap target would be a phantom affordance — visible ripple plus an
 * accessibility "Activate" action that does nothing. When the route
 * lands, add a `clickable` modifier (and the matching `onClick`
 * parameter) in the same commit that ships the
 * `SearchFeedsEffect.NavigateToFeed` emission.
 *
 * No query-substring highlighting — the Feeds tab doesn't do it (see
 * [net.kikin.nubecita.feature.search.impl.SearchFeedsContract]'s KDoc).
 */
@Composable
internal fun FeedRow(
    feed: FeedGeneratorUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
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
            val byline =
                if (feed.creatorDisplayName != null) {
                    stringResource(
                        R.string.search_feeds_row_byline_with_display_name,
                        feed.creatorDisplayName,
                        feed.creatorHandle,
                    )
                } else {
                    stringResource(R.string.search_feeds_row_byline_handle_only, feed.creatorHandle)
                }
            Text(
                text = byline,
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
                    pluralStringResource(
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

@Preview(name = "FeedRow — light", showBackground = true)
@Composable
private fun FeedRowLightPreview() {
    NubecitaTheme {
        FeedRow(feed = SAMPLE_FEED)
    }
}

@Preview(name = "FeedRow — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedRowDarkPreview() {
    NubecitaTheme {
        FeedRow(feed = SAMPLE_FEED)
    }
}

@Preview(name = "FeedRow — no creator display name", showBackground = true)
@Composable
private fun FeedRowNoCreatorDisplayNamePreview() {
    NubecitaTheme {
        FeedRow(feed = SAMPLE_FEED.copy(creatorDisplayName = null, description = null))
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
