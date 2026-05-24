package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot coverage for the tab strip extracted from
 * [SearchScreenContent]. Captures tab order/labels and the selected
 * indicator across both tabs × light/dark. Addresses the Copilot
 * review concern on PR #201 that the orchestration branch was
 * otherwise uncovered by automated tests — the integrated
 * `non-blank query + TabRow + tab content` shape can't be screenshot-
 * tested because the per-tab Screens hoist `hiltViewModel()`, which
 * doesn't resolve under the layoutlib-based screenshot harness.
 */
@PreviewTest
@Preview(name = "search-results-tabbar-posts-active-light", showBackground = true)
@Preview(
    name = "search-results-tabbar-posts-active-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchResultsTabBarPostsActiveScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchResultsTabBar(selectedTabIndex = 0, onSelectTab = {})
        }
    }
}

@PreviewTest
@Preview(name = "search-results-tabbar-people-active-light", showBackground = true)
@Preview(
    name = "search-results-tabbar-people-active-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchResultsTabBarPeopleActiveScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchResultsTabBar(selectedTabIndex = 1, onSelectTab = {})
        }
    }
}

@PreviewTest
@Preview(name = "search-results-tabbar-feeds-active-light", showBackground = true)
@Preview(
    name = "search-results-tabbar-feeds-active-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchResultsTabBarFeedsActiveScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchResultsTabBar(selectedTabIndex = 2, onSelectTab = {})
        }
    }
}
