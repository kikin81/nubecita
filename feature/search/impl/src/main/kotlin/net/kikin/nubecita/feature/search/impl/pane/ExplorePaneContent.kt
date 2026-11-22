package net.kikin.nubecita.feature.search.impl.pane

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.shimmer
import net.kikin.nubecita.feature.search.impl.DiscoverEvent
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.DiscoverState
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.ui.SuggestedAccountCard
import net.kikin.nubecita.feature.search.impl.ui.SuggestedFeedCard

/** How many cards a section shows before its "Show more" button. */
internal const val EXPLORE_COLLAPSED_COUNT = 4

private const val PEOPLE_COLUMNS = 2

/** The pane width from which the feed cards go two to a row. */
private val TWO_FEED_COLUMNS_MIN_WIDTH = 600.dp
private const val PLACEHOLDER_ROWS = 2
private const val FEED_ROW_KEY_PREFIX = "feeds:row:"
private val GRID_EDGE = 16.dp
private val GRID_GAP = 12.dp
private val PLACEHOLDER_CELL_HEIGHT = 148.dp

/** Feed cards need about 270dp each to keep their name readable: two columns from 600dp, else one. */
internal fun feedColumnsFor(paneWidth: Dp): Int = if (paneWidth >= TWO_FEED_COLUMNS_MIN_WIDTH) 2 else 1

/** What the pane body shows. */
internal enum class ExploreBody {
    /** Sections (loaded cards or loading placeholders); a failed or empty section is just hidden. */
    Content,

    /** Every section failed. */
    Retry,

    /** Nothing failed to the point of being everything, but nothing is there to suggest. */
    Nothing,
}

internal fun exploreBodyFor(
    feedsStatus: DiscoverSectionStatus,
    feedsHaveItems: Boolean,
    accountsStatus: DiscoverSectionStatus,
    accountsHaveItems: Boolean,
): ExploreBody {
    val phases =
        listOf(
            feedsStatus.sectionPhase(feedsHaveItems),
            accountsStatus.sectionPhase(accountsHaveItems),
        )
    return when {
        phases.any { it == SectionPhase.Content || it == SectionPhase.Loading } -> ExploreBody.Content
        phases.all { it == SectionPhase.Failed } -> ExploreBody.Retry
        else -> ExploreBody.Nothing
    }
}

/**
 * The Explore pane: suggested feeds, then people to follow, each a two-column grid built
 * from rows of two equally weighted cells inside this one [LazyColumn] (never a horizontal
 * carousel, so nothing is clipped at the pane edge).
 *
 * A section shows placeholder cells while it loads and is hidden when it came back empty
 * or failed. When every section failed a single retry state replaces both; when nothing
 * failed outright but there is nothing to suggest, a one-line message does.
 * [feedColumns] is the number of feed cards per row (people are always two): the caller
 * measures the pane once and passes it down. Each section shows [EXPLORE_COLLAPSED_COUNT] cards plus a "Show
 * more" button that expands it in place.
 *
 * Stateless: [listState] and the expansion flags are owned by the caller, which keeps them
 * in a ViewModel because the pane's slot cannot hold saved state.
 */
@Composable
internal fun ExplorePaneContent(
    state: DiscoverState,
    feedColumns: Int,
    feedsExpanded: Boolean,
    accountsExpanded: Boolean,
    onDiscoverEvent: (DiscoverEvent) -> Unit,
    onToggleFeeds: () -> Unit,
    onToggleAccounts: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val currentOnEvent by rememberUpdatedState(onDiscoverEvent)
    val feedsTitle = stringResource(R.string.explore_pane_feeds_title)
    val peopleTitle = stringResource(R.string.explore_pane_people_title)

    val feedsPhase = state.feedsStatus.sectionPhase(hasItems = state.feeds.isNotEmpty())
    val accountsPhase = state.accountsStatus.sectionPhase(hasItems = state.accounts.isNotEmpty())
    val body =
        exploreBodyFor(
            state.feedsStatus,
            state.feeds.isNotEmpty(),
            state.accountsStatus,
            state.accounts.isNotEmpty(),
        )

    val shownFeeds = if (feedsExpanded) state.feeds else state.feeds.take(EXPLORE_COLLAPSED_COUNT)
    val shownAccounts = if (accountsExpanded) state.accounts else state.accounts.take(EXPLORE_COLLAPSED_COUNT)

    // Feed cards load their preview posts when they scroll into view and settle. Keyed by the
    // first feed of each visible row; the row's second feed is read from the shown list.
    val currentShownFeeds by rememberUpdatedState(shownFeeds)
    LaunchedEffect(listState) {
        snapshotFlow {
            if (listState.isScrollInProgress) {
                emptyList()
            } else {
                listState.layoutInfo.visibleItemsInfo.mapNotNull { info ->
                    (info.key as? String)
                        ?.takeIf { it.startsWith(FEED_ROW_KEY_PREFIX) }
                        ?.removePrefix(FEED_ROW_KEY_PREFIX)
                }
            }
        }.distinctUntilChanged()
            .collect { firstUris ->
                val feeds = currentShownFeeds
                firstUris.forEach { firstUri ->
                    val start = feeds.indexOfFirst { it.feed.uri == firstUri }
                    if (start >= 0) {
                        feeds.subList(start, minOf(start + feedColumns, feeds.size)).forEach {
                            currentOnEvent(DiscoverEvent.OnFeedCardVisible(it.feed.uri))
                        }
                    }
                }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        if (body == ExploreBody.Retry) {
            item(key = "explore:retry", contentType = "retry") {
                ExploreRetry(
                    onRetry = { currentOnEvent(DiscoverEvent.OnRefresh) },
                    modifier = Modifier.fillParentMaxWidth(),
                )
            }
        } else if (body == ExploreBody.Nothing) {
            item(key = "explore:nothing", contentType = "nothing") {
                Text(
                    text = stringResource(R.string.explore_pane_nothing_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillParentMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                )
            }
        } else {
            gridSection(
                keyPrefix = "feeds",
                columns = feedColumns,
                title = feedsTitle,
                phase = feedsPhase,
                shown = shownFeeds,
                total = state.feeds.size,
                expanded = feedsExpanded,
                onToggle = onToggleFeeds,
                itemKey = { it.feed.uri },
                rowKeyPrefix = FEED_ROW_KEY_PREFIX,
            ) { discoverFeed ->
                SuggestedFeedCard(
                    discoverFeed = discoverFeed,
                    onFeedClick = {
                        currentOnEvent(DiscoverEvent.OnFeedTapped(discoverFeed.feed.uri, discoverFeed.feed.displayName))
                    },
                    onPinClick = { currentOnEvent(DiscoverEvent.OnPinTapped(discoverFeed.feed.uri)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            gridSection(
                keyPrefix = "accounts",
                columns = PEOPLE_COLUMNS,
                title = peopleTitle,
                phase = accountsPhase,
                shown = shownAccounts,
                total = state.accounts.size,
                expanded = accountsExpanded,
                onToggle = onToggleAccounts,
                itemKey = { it.did },
                rowKeyPrefix = "accounts:row:",
            ) { account ->
                SuggestedAccountCard(
                    account = account,
                    onAccountClick = { currentOnEvent(DiscoverEvent.OnAccountTapped(did = account.did, handle = account.handle)) },
                    onFollowClick = { currentOnEvent(DiscoverEvent.OnFollowTapped(did = account.did)) },
                    onDismissClick = { currentOnEvent(DiscoverEvent.OnAccountDismissed(did = account.did)) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private enum class SectionPhase { Content, Loading, Failed, Empty }

private fun DiscoverSectionStatus.sectionPhase(hasItems: Boolean): SectionPhase =
    when {
        this == DiscoverSectionStatus.Loaded && hasItems -> SectionPhase.Content
        this == DiscoverSectionStatus.Idle || this == DiscoverSectionStatus.Loading -> SectionPhase.Loading
        this == DiscoverSectionStatus.Error -> SectionPhase.Failed
        else -> SectionPhase.Empty
    }

/**
 * One section as items of the pane's lazy column: a heading, then rows of two weighted
 * cells (an odd last row keeps its single cell at half width beside an empty weighted
 * spacer), then a "Show more" / "Show less" button when there are more than
 * [EXPLORE_COLLAPSED_COUNT] items. Keys are prefixed by section so the two grids cannot
 * collide.
 */
private fun <T> LazyListScope.gridSection(
    keyPrefix: String,
    columns: Int,
    title: String,
    phase: SectionPhase,
    shown: List<T>,
    total: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    itemKey: (T) -> String,
    rowKeyPrefix: String,
    cell: @Composable (T) -> Unit,
) {
    if (phase == SectionPhase.Failed || phase == SectionPhase.Empty) return
    item(key = "$keyPrefix:header", contentType = "header") { SectionHeading(title) }
    if (phase == SectionPhase.Loading) {
        items(
            count = PLACEHOLDER_ROWS,
            key = { "$keyPrefix:placeholder:$it" },
            contentType = { "placeholder-row" },
        ) {
            GridRow {
                repeat(columns) { PlaceholderCell(Modifier.weight(1f)) }
            }
        }
        return
    }
    val rows = shown.chunked(columns)
    items(
        count = rows.size,
        key = { index -> rowKeyPrefix + itemKey(rows[index].first()) },
        contentType = { "$keyPrefix-row" },
    ) { index ->
        val row = rows[index]
        // Cells of a row share the taller one's height (the cards stretch to it).
        GridRow(equalHeight = columns > 1) {
            repeat(columns) { column ->
                if (column < row.size) {
                    Box(Modifier.weight(1f).fillMaxHeight()) { cell(row[column]) }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
    if (total > EXPLORE_COLLAPSED_COUNT) {
        item(key = "$keyPrefix:toggle", contentType = "toggle") {
            TextButton(
                onClick = onToggle,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Text(stringResource(if (expanded) R.string.trending_show_less else R.string.trending_show_more))
            }
        }
    }
}

@Composable
private fun GridRow(
    modifier: Modifier = Modifier,
    equalHeight: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (equalHeight) Modifier.height(IntrinsicSize.Max) else Modifier)
                .padding(horizontal = GRID_EDGE, vertical = GRID_GAP / 2),
        horizontalArrangement = Arrangement.spacedBy(GRID_GAP),
        content = content,
    )
}

@Composable
private fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics { heading() },
    )
}

@Composable
private fun PlaceholderCell(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .height(PLACEHOLDER_CELL_HEIGHT)
                .clip(RoundedCornerShape(12.dp))
                .shimmer(),
    )
}

@Composable
private fun ExploreRetry(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.explore_pane_error_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.explore_pane_retry))
        }
    }
}
