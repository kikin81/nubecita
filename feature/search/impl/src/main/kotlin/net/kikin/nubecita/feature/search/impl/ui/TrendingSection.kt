package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.designsystem.component.shimmer
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.TrendUi

/** How many trends the compact landing shows before "Show more". */
internal const val COLLAPSED_TREND_COUNT = 5

/** Number of placeholder rows shown while the trends load. */
private const val PLACEHOLDER_TREND_COUNT = 3

private const val SUBTITLE_SEPARATOR = " · "

/**
 * The Trending section of the Search landing, as items of the landing's one `LazyColumn`.
 *
 * - [DiscoverSectionStatus.Loading]: a heading and three shimmer rows.
 * - [DiscoverSectionStatus.Loaded] with trends: a heading and the rows. When [collapsible]
 *   the list shows [COLLAPSED_TREND_COUNT] rows plus a "Show more" button (and "Show less"
 *   once [expanded]); otherwise every trend, no button.
 * - Anything else (Idle, Empty, Error): nothing, not even the heading.
 */
internal fun LazyListScope.trendingSection(
    trends: ImmutableList<TrendUi>,
    status: DiscoverSectionStatus,
    expanded: Boolean,
    collapsible: Boolean,
    onToggleExpanded: () -> Unit,
    onTrendTap: (TrendUi) -> Unit,
) {
    val loaded = status == DiscoverSectionStatus.Loaded && trends.isNotEmpty()
    val loading = status == DiscoverSectionStatus.Loading
    if (!loaded && !loading) return

    item(key = "trending_header", contentType = "trending_header") {
        TrendingHeader()
    }
    if (loading && !loaded) {
        items(
            count = PLACEHOLDER_TREND_COUNT,
            key = { "trending_placeholder_$it" },
            contentType = { "trending_placeholder" },
        ) {
            TrendRowPlaceholder()
        }
        return
    }
    val shown = if (collapsible && !expanded) trends.take(COLLAPSED_TREND_COUNT) else trends
    items(
        count = shown.size,
        key = { index -> "trend:" + shown[index].id },
        contentType = { "trend" },
    ) { index ->
        val trend = shown[index]
        TrendRow(rank = index + 1, trend = trend, onClick = { onTrendTap(trend) })
    }
    if (collapsible && trends.size > COLLAPSED_TREND_COUNT) {
        item(key = "trending_toggle", contentType = "trending_toggle") {
            TextButton(
                onClick = onToggleExpanded,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            if (expanded) R.string.trending_show_less else R.string.trending_show_more,
                        ),
                )
            }
        }
    }
}

@Composable
private fun TrendingHeader(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.trending_section_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics { heading() },
    )
}

/**
 * One ranked trend: rank, display name, and a de-emphasised second line made of the parts
 * that exist (see [trendSubtitleParts]). The whole row is one click target, at least 48dp
 * tall, announced as a single sentence.
 */
@Composable
internal fun TrendRow(
    rank: Int,
    trend: TrendUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hotLabel = if (trend.isHot) stringResource(R.string.trending_hot_label) else null
    val countText = rememberCompactCount(trend.postCount)
    val countLabel =
        if (trend.postCount > 0L) stringResource(R.string.trending_post_count, countText) else null
    val subtitle = trendSubtitleParts(hotLabel, trend.category, countLabel)
    val subtitleText = subtitle.joinToString(SUBTITLE_SEPARATOR)
    val spoken = stringResource(R.string.trending_row_content_desc, rank, trend.displayName)
    val contentDescription =
        if (subtitle.isEmpty()) spoken else spoken + ", " + subtitle.joinToString(", ")

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = trend.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitleText.isNotEmpty()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun TrendRowPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.width(24.dp).height(14.dp).shimmer())
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).shimmer())
            Box(Modifier.fillMaxWidth(0.35f).height(10.dp).shimmer())
        }
    }
}

/**
 * The parts of a trend row's second line, in order: the hot mark, the category (first
 * letter capitalised, otherwise as sent), the post-count sentence. Missing, null and blank
 * parts are left out; the caller joins what remains.
 */
internal fun trendSubtitleParts(
    hotLabel: String?,
    category: String?,
    postCountLabel: String?,
): List<String> =
    listOfNotNull(
        hotLabel?.takeIf { it.isNotBlank() },
        category?.takeIf { it.isNotBlank() }?.trim()?.replaceFirstChar { it.titlecase() },
        postCountLabel?.takeIf { it.isNotBlank() },
    )

// ---------------------------------------------------------------------------
// Shared fixtures for the screenshot tests
// ---------------------------------------------------------------------------

internal val SAMPLE_TREND_HOT =
    TrendUi(
        topic = "lunar-eclipse",
        displayName = "Lunar eclipse",
        link = "/profile/did:plc:bench1/feed/eclipse",
        category = "science",
        postCount = 48_200L,
        isHot = true,
    )

internal val SAMPLE_TREND_NO_CATEGORY =
    TrendUi(
        topic = "spring-roll-recipes",
        displayName = "Spring roll recipes",
        link = "/search?q=spring%20roll%20recipes",
        category = null,
        postCount = 3_100L,
        isHot = false,
    )

/** Ten ranked trends: the first is hot, the third has no category. */
internal val SAMPLE_TRENDS: ImmutableList<TrendUi> =
    persistentListOf(
        SAMPLE_TREND_HOT,
        trendSample("world-cup", "World Cup 2026", "sports", 120_500L),
        SAMPLE_TREND_NO_CATEGORY,
        trendSample("budget-vote", "Budget vote", "politics", 9_800L),
        trendSample("new-album", "New album Friday", "entertainment", 15_400L),
        trendSample("rust-2027", "Rust 2027 edition", "technology", 2_300L),
        trendSample("marathon", "City marathon", "sports", 6_700L),
        trendSample("northern-lights", "Northern lights", "science", 21_000L),
        trendSample("book-club", "Book club picks", "books", 1_200L),
        trendSample("coffee-prices", "Coffee prices", "business", 4_500L),
    )

private fun trendSample(
    topic: String,
    name: String,
    category: String,
    count: Long,
) = TrendUi(
    topic = topic,
    displayName = name,
    link = "/search?q=$topic",
    category = category,
    postCount = count,
    isHot = false,
)
