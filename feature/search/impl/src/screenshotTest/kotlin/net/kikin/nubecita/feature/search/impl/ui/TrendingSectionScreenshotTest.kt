package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import net.kikin.nubecita.feature.search.impl.SearchPhase
import net.kikin.nubecita.feature.search.impl.SearchScreenContent
import net.kikin.nubecita.feature.search.impl.data.TrendUi

@Composable
private fun TrendingFixture(
    trends: ImmutableList<TrendUi>,
    status: DiscoverSectionStatus = DiscoverSectionStatus.Loaded,
    expanded: Boolean = false,
    collapsible: Boolean = true,
) {
    NubecitaCanvasPreviewTheme {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            trendingSection(
                trends = trends,
                status = status,
                expanded = expanded,
                collapsible = collapsible,
                onToggleExpanded = {},
                onTrendTap = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "trending-section-collapsed-light", showBackground = true, heightDp = 420)
@Preview(name = "trending-section-collapsed-dark", showBackground = true, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "trending-section-collapsed-es419", showBackground = true, heightDp = 420, locale = "b+es+419")
@Composable
private fun TrendingSectionCollapsedScreenshot() {
    TrendingFixture(trends = SAMPLE_TRENDS)
}

@PreviewTest
@Preview(name = "trending-section-expanded-light", showBackground = true, heightDp = 760)
@Preview(name = "trending-section-expanded-dark", showBackground = true, heightDp = 760, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrendingSectionExpandedScreenshot() {
    TrendingFixture(trends = SAMPLE_TRENDS, expanded = true)
}

@PreviewTest
@Preview(name = "trending-row-no-category-light", showBackground = true, heightDp = 100)
@Composable
private fun TrendingRowNoCategoryScreenshot() {
    TrendingFixture(trends = persistentListOf(SAMPLE_TREND_NO_CATEGORY), collapsible = false)
}

@PreviewTest
@Preview(name = "trending-row-hot-light", showBackground = true, heightDp = 100)
@Preview(name = "trending-row-hot-dark", showBackground = true, heightDp = 100, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrendingRowHotScreenshot() {
    TrendingFixture(trends = persistentListOf(SAMPLE_TREND_HOT), collapsible = false)
}

@PreviewTest
@Preview(name = "trending-section-loading-light", showBackground = true, heightDp = 260)
@Preview(name = "trending-section-loading-dark", showBackground = true, heightDp = 260, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TrendingSectionLoadingScreenshot() {
    TrendingFixture(trends = persistentListOf(), status = DiscoverSectionStatus.Loading)
}

@PreviewTest
@Preview(name = "search-landing-phone-light", showBackground = true, heightDp = 900)
@Preview(name = "search-landing-phone-dark", showBackground = true, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchLandingPhoneScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf("kotlin", "compose"),
                onEvent = {},
                onClearQueryRequest = {},
                discoverState = DISCOVER_POPULATED_STATE.copy(trends = SAMPLE_TRENDS, trendsStatus = DiscoverSectionStatus.Loaded),
                onDiscoverEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-landing-list-pane-light", showBackground = true, widthDp = 375, heightDp = 900)
@Preview(name = "search-landing-list-pane-dark", showBackground = true, widthDp = 375, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchLandingListPaneScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf("kotlin", "compose"),
                onEvent = {},
                onClearQueryRequest = {},
                isAtLeastMedium = true,
                discoverState = DISCOVER_POPULATED_STATE.copy(trends = SAMPLE_TRENDS, trendsStatus = DiscoverSectionStatus.Loaded),
                onDiscoverEvent = {},
            )
        }
    }
}
