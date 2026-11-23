package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.text.rememberCompactCount
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.component.shimmer
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.ReplyFilter
import net.kikin.nubecita.feature.search.impl.data.SearchTab
import net.kikin.nubecita.feature.search.impl.ui.ActorRow
import net.kikin.nubecita.feature.search.impl.ui.FeedRow
import net.kikin.nubecita.feature.search.impl.ui.dateLabel
import net.kikin.nubecita.feature.search.impl.ui.filterChipState
import net.kikin.nubecita.feature.search.impl.ui.shownHitsTotal
import net.kikin.nubecita.feature.search.impl.ui.sortLabel

private val CARD_SHAPE = RoundedCornerShape(16.dp)
private val PLACEHOLDER_ROW_HEIGHT = 64.dp
private val PLACEHOLDER_CARD_HEIGHT = 132.dp
private val CHIP_ICON_SIZE = 18.dp
private const val QUERY_MAX_LINES = 3
private const val POST_TEXT_MAX_LINES = 4

/**
 * True when all three sections finished, none failed, and none has anything to show: the
 * pane then says so once instead of showing three empty sections.
 */
internal fun SearchPaneMode.Summary.hasNoResults(): Boolean =
    postsStatus == SummarySectionStatus.Loaded &&
        peopleStatus == SummarySectionStatus.Loaded &&
        feedsStatus == SummarySectionStatus.Loaded &&
        topPost == null &&
        people.isEmpty() &&
        feeds.isEmpty()

/**
 * The Summary: the submitted search in brief, for the second pane of a wide window. A header
 * (the query, the estimated count, the active filters as removable chips and "Edit filters"),
 * the top post as a small card, then the first people and feeds, each with "See all".
 *
 * Each section is independent: it shows placeholders while it loads, a one-line message with
 * Retry when it failed, and is hidden when it has nothing. Stateless: [listState] is owned by
 * the caller (the pane's slot cannot hold saved state), and every tap is an event. The people
 * rows carry the viewer's follow state already; [viewerDid]'s own row has no Follow button.
 *
 * Insets are the caller's: the list starts below the status bar, and [contentPadding] only
 * carries the bottom inset.
 */
@Composable
internal fun SummaryPaneContent(
    summary: SearchPaneMode.Summary,
    viewerDid: String?,
    onEvent: (SearchPaneEvent) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val query = summary.search.query

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        item(key = "summary:header", contentType = "header") {
            SummaryHeader(
                query = query,
                filters = summary.search.filters,
                hitsTotal = summary.hitsTotal,
                onRemoveFilter = { currentOnEvent(SearchPaneEvent.FilterRemoved(it)) },
                onEditFilters = { currentOnEvent(SearchPaneEvent.EditFiltersClicked) },
            )
        }
        if (summary.hasNoResults()) {
            item(key = "summary:empty", contentType = "empty") {
                Text(
                    text = stringResource(R.string.summary_pane_no_results, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                )
            }
            return@LazyColumn
        }
        postsSection(summary, onEvent = { currentOnEvent(it) })
        peopleSection(summary, viewerDid, onEvent = { currentOnEvent(it) })
        feedsSection(summary, onEvent = { currentOnEvent(it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryHeader(
    query: String,
    filters: PostSearchFilters,
    hitsTotal: Long?,
    onRemoveFilter: (SummaryFilter) -> Unit,
    onEditFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayLocale = LocalLocale.current.platformLocale
    val chips = remember(filters, displayLocale) { filterChipState(filters, displayLocale) }
    val active = remember(filters) { filters.activeSummaryFilters() }
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.summary_pane_results_for),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = query,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = QUERY_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        // The same rule the Posts tab uses: no estimate, or zero, shows no count at all.
        shownHitsTotal(hitsTotal)?.let { count ->
            Text(
                text = stringResource(R.string.summary_pane_count_line, rememberCompactCount(count), sortLabel(filters.sort)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            active.forEach { filter ->
                val label = summaryFilterLabel(filter, filters, chips.languageName)
                RemovableFilterChip(label = label, onRemove = { onRemoveFilter(filter) })
            }
            AssistChip(
                onClick = onEditFilters,
                label = { Text(stringResource(R.string.summary_pane_edit_filters), maxLines = 1) },
                leadingIcon = {
                    NubecitaIcon(name = NubecitaIconName.Menu, contentDescription = null, opticalSize = CHIP_ICON_SIZE)
                },
            )
        }
    }
}

/** The chip's label is the one the filter row shows for the same filter. */
@Composable
private fun summaryFilterLabel(
    filter: SummaryFilter,
    filters: PostSearchFilters,
    languageName: String?,
): String =
    when (filter) {
        SummaryFilter.Sort -> sortLabel(filters.sort)
        SummaryFilter.Following -> stringResource(R.string.search_filter_following)
        SummaryFilter.Media -> stringResource(R.string.search_filter_media)
        SummaryFilter.Video -> stringResource(R.string.search_filter_video)
        SummaryFilter.Replies ->
            stringResource(
                if (filters.replies == ReplyFilter.HIDE) R.string.search_filter_replies_hide else R.string.search_filter_replies_only,
            )
        SummaryFilter.Language -> languageName ?: filters.language.orEmpty()
        SummaryFilter.Date -> dateLabel(filters.date)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovableFilterChip(
    label: String,
    onRemove: () -> Unit,
) {
    val description = stringResource(R.string.summary_pane_remove_filter, label)
    InputChip(
        selected = true,
        onClick = onRemove,
        label = { Text(label, maxLines = 1) },
        trailingIcon = {
            NubecitaIcon(name = NubecitaIconName.Close, contentDescription = null, opticalSize = CHIP_ICON_SIZE)
        },
        modifier = Modifier.semantics { contentDescription = description },
    )
}

private fun LazyListScope.postsSection(
    summary: SearchPaneMode.Summary,
    onEvent: (SearchPaneEvent) -> Unit,
) {
    when (summary.postsStatus) {
        SummarySectionStatus.Loading -> {
            item(key = "posts:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_top_post_title) }
            item(key = "posts:placeholder", contentType = "post-placeholder") {
                PlaceholderBlock(PLACEHOLDER_CARD_HEIGHT, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), CARD_SHAPE)
            }
        }
        SummarySectionStatus.Error -> {
            item(key = "posts:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_top_post_title) }
            item(key = "posts:error", contentType = "error") {
                SectionError(R.string.summary_pane_posts_error) { onEvent(SearchPaneEvent.RetrySection(SummarySection.Posts)) }
            }
        }
        SummarySectionStatus.Loaded -> {
            val post = summary.topPost ?: return
            item(key = "posts:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_top_post_title) }
            item(key = "posts:card", contentType = "post-card") {
                TopPostCard(
                    item = post,
                    onClick = { onEvent(SearchPaneEvent.PostTapped(post.post.id)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun LazyListScope.peopleSection(
    summary: SearchPaneMode.Summary,
    viewerDid: String?,
    onEvent: (SearchPaneEvent) -> Unit,
) {
    when (summary.peopleStatus) {
        SummarySectionStatus.Loading -> {
            item(key = "people:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_people_title) }
            placeholderRows("people")
        }
        SummarySectionStatus.Error -> {
            item(key = "people:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_people_title) }
            item(key = "people:error", contentType = "error") {
                SectionError(R.string.summary_pane_people_error) { onEvent(SearchPaneEvent.RetrySection(SummarySection.People)) }
            }
        }
        SummarySectionStatus.Loaded -> {
            if (summary.people.isEmpty()) return
            item(key = "people:heading", contentType = "heading") {
                SectionHeading(
                    title = R.string.summary_pane_people_title,
                    seeAllDescription = R.string.summary_pane_see_all_people_description,
                    onSeeAll = { onEvent(SearchPaneEvent.SeeAllClicked(SearchTab.PEOPLE)) },
                )
            }
            items(
                count = summary.people.size,
                key = { index -> "people:row:${summary.people[index].did}" },
                contentType = { "people-row" },
            ) { index ->
                val actor = summary.people[index]
                ActorRow(
                    actor = actor,
                    query = summary.search.query,
                    onClick = { onEvent(SearchPaneEvent.ActorTapped(actor.handle)) },
                    // The viewer can't follow themselves: their own row offers no button.
                    onFollowClick = if (actor.did == viewerDid) null else ({ onEvent(SearchPaneEvent.FollowTapped(actor.did)) }),
                )
            }
        }
    }
}

private fun LazyListScope.feedsSection(
    summary: SearchPaneMode.Summary,
    onEvent: (SearchPaneEvent) -> Unit,
) {
    when (summary.feedsStatus) {
        SummarySectionStatus.Loading -> {
            item(key = "feeds:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_feeds_title) }
            placeholderRows("feeds")
        }
        SummarySectionStatus.Error -> {
            item(key = "feeds:heading", contentType = "heading") { SectionHeading(R.string.summary_pane_feeds_title) }
            item(key = "feeds:error", contentType = "error") {
                SectionError(R.string.summary_pane_feeds_error) { onEvent(SearchPaneEvent.RetrySection(SummarySection.Feeds)) }
            }
        }
        SummarySectionStatus.Loaded -> {
            if (summary.feeds.isEmpty()) return
            item(key = "feeds:heading", contentType = "heading") {
                SectionHeading(
                    title = R.string.summary_pane_feeds_title,
                    seeAllDescription = R.string.summary_pane_see_all_feeds_description,
                    onSeeAll = { onEvent(SearchPaneEvent.SeeAllClicked(SearchTab.FEEDS)) },
                )
            }
            items(
                count = summary.feeds.size,
                key = { index -> "feeds:row:${summary.feeds[index].uri}" },
                contentType = { "feeds-row" },
            ) { index ->
                val feed = summary.feeds[index]
                FeedRow(
                    feed = feed,
                    onClick = { onEvent(SearchPaneEvent.FeedTapped(feed.uri, feed.displayName)) },
                    onPinClick = { onEvent(SearchPaneEvent.PinTapped(feed.uri)) },
                )
            }
        }
    }
}

private fun LazyListScope.placeholderRows(prefix: String) {
    items(
        count = SUMMARY_ROWS,
        key = { "$prefix:placeholder:$it" },
        contentType = { "row-placeholder" },
    ) {
        PlaceholderBlock(PLACEHOLDER_ROW_HEIGHT, Modifier.padding(horizontal = 20.dp, vertical = 6.dp), RoundedCornerShape(12.dp))
    }
}

@Composable
private fun PlaceholderBlock(
    height: Dp,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .shimmer(),
    ) {}
}

@Composable
private fun SectionHeading(
    title: Int,
    modifier: Modifier = Modifier,
    seeAllDescription: Int? = null,
    onSeeAll: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (onSeeAll != null && seeAllDescription != null) {
            val description = stringResource(seeAllDescription)
            TextButton(
                onClick = onSeeAll,
                modifier = Modifier.semantics { contentDescription = description },
            ) {
                Text(stringResource(R.string.summary_pane_see_all))
            }
        }
    }
}

@Composable
private fun SectionError(
    message: Int,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.summary_pane_retry)) }
    }
}

/**
 * The top post as a compact preview: author, the text capped at four lines, and one muted
 * line of counts. The whole card is the one target and opens the thread; there are no
 * like / repost / share controls here, and media is not rendered.
 */
@Composable
private fun TopPostCard(
    item: FeedItemUi.Single,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val post = item.post
    val author = post.author
    val name = author.displayName.takeIf { it.isNotBlank() } ?: author.handle
    val openLabel = stringResource(R.string.summary_pane_top_post_description, name)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(CARD_SHAPE)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClickLabel = openLabel, onClick = onClick)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NubecitaAvatar(
                model = author.avatarUrl,
                contentDescription = null,
                size = 40.dp,
                fallback = avatarFallbackFor(author),
                modifier = Modifier.size(40.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "@${author.handle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = post.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = POST_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text =
                stringResource(
                    R.string.summary_pane_post_counts,
                    rememberCompactCount(post.stats.replyCount.toLong()),
                    rememberCompactCount(post.stats.repostCount.toLong()),
                    rememberCompactCount(post.stats.likeCount.toLong()),
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
