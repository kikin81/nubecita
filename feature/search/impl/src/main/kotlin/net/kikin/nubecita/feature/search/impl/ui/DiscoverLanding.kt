package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.feature.search.impl.DiscoverEvent
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.DiscoverState
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.TrendUi

/**
 * The Search landing's scrolling body: one [LazyColumn] holding, top to bottom, the recent
 * searches, the Trending section, and (when [showCards]) the suggested feeds and people
 * carousels, with an empty hint when nothing at all is available.
 *
 * [showCards] is `false` on Medium/Expanded windows, where this is the list pane and shows
 * trends only: the suggestion cards live in the second pane there. It also decides whether
 * the Trending list collapses to a few rows with a "Show more" button (phones) or always
 * shows every trend (wide).
 */
@Composable
internal fun DiscoverLanding(
    state: DiscoverState,
    recentSearches: ImmutableList<String>,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onEvent: (DiscoverEvent) -> Unit,
    showCards: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val onToggleExpanded = remember { { currentOnEvent(DiscoverEvent.OnTrendsExpansionToggled) } }
    val onTrendTap = remember { { trend: TrendUi -> currentOnEvent(DiscoverEvent.OnTrendTapped(trend)) } }
    val showEmptyHint = recentSearches.isEmpty() && state.hasNothingToShow(showCards)

    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (recentSearches.isNotEmpty()) {
            item(key = "recents", contentType = "recents") {
                RecentSearchChipStrip(
                    items = recentSearches,
                    onChipTap = onChipTap,
                    onChipRemove = onChipRemove,
                    onClearAll = onClearAll,
                )
            }
        }
        trendingSection(
            trends = state.trends,
            status = state.trendsStatus,
            expanded = state.trendsExpanded,
            collapsible = showCards,
            onToggleExpanded = onToggleExpanded,
            onTrendTap = onTrendTap,
        )
        if (showCards &&
            (state.accountsStatus == DiscoverSectionStatus.Loaded || state.feedsStatus == DiscoverSectionStatus.Loaded)
        ) {
            item(key = "discover_sections", contentType = "discover_sections") {
                DiscoverSections(state = state, onEvent = onEvent)
            }
        }
        if (showEmptyHint) {
            item(key = "empty_hint", contentType = "empty_hint") {
                DiscoverEmptyHint(
                    modifier =
                        Modifier
                            .fillParentMaxSize()
                            .padding(horizontal = 32.dp),
                )
            }
        }
    }
}

private fun DiscoverSectionStatus.isSettledWithoutContent(): Boolean = this is DiscoverSectionStatus.Empty || this is DiscoverSectionStatus.Error

/** Every section this layout shows came back empty or failed. */
private fun DiscoverState.hasNothingToShow(showCards: Boolean): Boolean =
    trendsStatus.isSettledWithoutContent() &&
        (!showCards || (accountsStatus.isSettledWithoutContent() && feedsStatus.isSettledWithoutContent()))

/**
 * Empty-state hint shown when the Discover body has nothing to show
 * and no recent searches. Prompts the user to pull to refresh.
 */
@Composable
private fun DiscoverEmptyHint(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.discover_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
