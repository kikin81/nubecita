package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.component.shimmer
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.impl.DiscoverFeedUi
import net.kikin.nubecita.feature.search.impl.FeedPreviewStatus
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.FeedPreviewPostUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedFeedUi

private val CARD_WIDTH = 300.dp
private val FEED_AVATAR_SIZE = 40.dp

/**
 * Google-News-style suggested-feed card for the Discover tab's feeds carousel.
 *
 * **Header row:** 40dp rounded-square feed avatar + Column(display name +
 * creator handle) + [IconToggleButton] (Bookmark icon, filled when pinned).
 *
 * **Body:** when [DiscoverFeedUi.previewStatus] is [FeedPreviewStatus.Loaded],
 * up to 3 [FeedPreviewPostUi] rows (author avatar + @handle + text snippet).
 * When [FeedPreviewStatus.Loading] or [FeedPreviewStatus.Idle], three shimmer
 * placeholder rows animate to signal the in-flight fetch. On
 * [FeedPreviewStatus.Error], the body is empty — the card still shows its
 * header so the pin toggle remains reachable.
 *
 * Click targets:
 * - Whole card tap → [onFeedTapped] (→ `OnFeedTapped` event). Does not fire
 *   when tapping the Pin [IconToggleButton] — child clicks consume the event.
 * - Pin toggle → [onPinTapped] (→ `OnPinTapped` event).
 *
 * Surface token: [MaterialTheme.colorScheme.surfaceContainer] (item card).
 * Shimmer base: [MaterialTheme.colorScheme.surfaceContainerHighest] via the
 * `:designsystem` [Modifier.shimmer] extension (reads theme colors
 * automatically, no caller wiring).
 */
@Composable
internal fun SuggestedFeedCard(
    discoverFeed: DiscoverFeedUi,
    onFeedClick: () -> Unit,
    onPinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feed = discoverFeed.feed
    val cardContentDesc = stringResource(R.string.discover_feed_card_content_desc, feed.displayName)
    val pinContentDesc =
        if (feed.isPinned) {
            stringResource(R.string.discover_pinned_content_desc)
        } else {
            stringResource(R.string.discover_pin_content_desc)
        }
    ElevatedCard(
        modifier =
            modifier
                .width(CARD_WIDTH)
                .clickable(onClick = onFeedClick)
                .semantics { contentDescription = cardContentDesc },
        colors =
            CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: avatar + name/creator + pin toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                NubecitaAsyncImage(
                    model = feed.avatarUrl,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(FEED_AVATAR_SIZE)
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
                        text = "@${feed.creatorHandle}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconToggleButton(
                    checked = feed.isPinned,
                    onCheckedChange = { onPinClick() },
                ) {
                    NubecitaIcon(
                        name = NubecitaIconName.Bookmark,
                        contentDescription = pinContentDesc,
                        filled = feed.isPinned,
                    )
                }
            }
            // Body: preview posts or shimmer
            FeedCardBody(
                previewStatus = discoverFeed.previewStatus,
                preview = discoverFeed.preview,
            )
        }
    }
}

@Composable
private fun FeedCardBody(
    previewStatus: FeedPreviewStatus,
    preview: ImmutableList<FeedPreviewPostUi>?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (previewStatus) {
            FeedPreviewStatus.Loaded -> {
                preview?.take(3)?.forEach { post ->
                    FeedPreviewRow(post = post)
                }
            }
            FeedPreviewStatus.Loading, FeedPreviewStatus.Idle -> {
                repeat(3) { FeedPreviewShimmerRow() }
            }
            FeedPreviewStatus.Error -> {
                // Empty body — feed card still usable via header + pin toggle.
            }
        }
    }
}

@Composable
private fun FeedPreviewRow(
    post: FeedPreviewPostUi,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NubecitaAvatar(
            model = post.authorAvatarUrl,
            contentDescription = null,
            size = 24.dp,
            fallback =
                avatarFallbackFor(
                    // Derive a stable pseudo-DID from handle for the hue function.
                    did = "handle:${post.authorHandle}",
                    handle = post.authorHandle,
                    displayName = null,
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.discover_feed_preview_author_handle, post.authorHandle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedPreviewShimmerRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Avatar shimmer
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .shimmer(),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Handle line shimmer
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.35f)
                        .height(10.dp)
                        .shimmer(),
            )
            // Text line shimmer
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(10.dp)
                        .shimmer(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(10.dp)
                        .shimmer(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "FeedCard — preview loaded, not pinned", showBackground = true)
@Composable
private fun FeedCardLoadedNotPinnedPreview() {
    NubecitaTheme {
        SuggestedFeedCard(
            discoverFeed = SAMPLE_FEED_LOADED,
            onFeedClick = {},
            onPinClick = {},
        )
    }
}

@Preview(name = "FeedCard — preview loaded, pinned", showBackground = true)
@Composable
private fun FeedCardLoadedPinnedPreview() {
    NubecitaTheme {
        SuggestedFeedCard(
            discoverFeed = SAMPLE_FEED_LOADED.copy(feed = SAMPLE_FEED_LOADED.feed.copy(isPinned = true)),
            onFeedClick = {},
            onPinClick = {},
        )
    }
}

@Preview(name = "FeedCard — preview loading (shimmer)", showBackground = true)
@Composable
private fun FeedCardLoadingPreview() {
    NubecitaTheme {
        SuggestedFeedCard(
            discoverFeed = SAMPLE_FEED_LOADING,
            onFeedClick = {},
            onPinClick = {},
        )
    }
}

@Preview(
    name = "FeedCard — dark, preview loaded",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedCardDarkPreview() {
    NubecitaTheme {
        SuggestedFeedCard(
            discoverFeed = SAMPLE_FEED_LOADED,
            onFeedClick = {},
            onPinClick = {},
        )
    }
}

private val SAMPLE_PREVIEW_POSTS =
    persistentListOf(
        FeedPreviewPostUi(
            authorHandle = "scientist.bsky.social",
            authorAvatarUrl = null,
            text = "Fascinating new results from the Webb telescope — thread 🧵",
            thumbnailUrl = null,
        ),
        FeedPreviewPostUi(
            authorHandle = "labnotes.bsky.social",
            authorAvatarUrl = null,
            text = "New preprint on CRISPR base-editing efficiency just dropped.",
            thumbnailUrl = null,
        ),
        FeedPreviewPostUi(
            authorHandle = "devlog.bsky.social",
            authorAvatarUrl = null,
            text = "Shipping a new open-source library today. Check it out!",
            thumbnailUrl = null,
        ),
    )

internal val SAMPLE_FEED_LOADED =
    DiscoverFeedUi(
        feed =
            SuggestedFeedUi(
                uri = "at://did:plc:test1/app.bsky.feed.generator/science",
                displayName = "Science",
                creatorHandle = "labnotes.bsky.social",
                avatarUrl = null,
                description = "Peer-reviewed papers, preprints, and the people behind them.",
                isPinned = false,
            ),
        preview = SAMPLE_PREVIEW_POSTS,
        previewStatus = FeedPreviewStatus.Loaded,
    )

internal val SAMPLE_FEED_LOADING =
    DiscoverFeedUi(
        feed =
            SuggestedFeedUi(
                uri = "at://did:plc:test2/app.bsky.feed.generator/art",
                displayName = "Art & Design",
                creatorHandle = "studio.bsky.social",
                avatarUrl = null,
                description = null,
                isPinned = false,
            ),
        preview = null,
        previewStatus = FeedPreviewStatus.Loading,
    )

// Expose width constant for carousel layout calculations.
internal val SUGGESTED_FEED_CARD_WIDTH = CARD_WIDTH

// Expose a no-op spacer for carousels that want equal-height shimmer placeholder.
@Composable
internal fun SuggestedFeedCardPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .width(CARD_WIDTH)
                .height(220.dp),
    ) {
        Spacer(modifier = Modifier.fillMaxWidth())
    }
}
