package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

// vrba.8 note: the integrated [SearchPhase.Results] variant (TabRow +
// HorizontalPager mounting SearchPostsScreen / SearchActorsScreen) is
// deliberately NOT screenshot-tested here. Both per-tab Screens hoist
// `hiltViewModel<>()` which cannot resolve under the layoutlib-based
// screenshot harness (no Hilt graph). Visual coverage of the tab
// bodies is provided by the per-component baselines in
// `ui/PostsTabContentScreenshotTest` + `ui/PeopleTabContentScreenshotTest`.
// On-device verification covers the integrated swipe + tab-state path.
//
// SearchBar note: the expanded typing overlay ([ExpandedFullScreenSearchBar]
// hosting the recents list or [SearchTypeaheadScreen]) renders in a popup
// window that layoutlib cannot capture, so it is NOT screenshot-tested. The
// typeahead body is covered directly in `ui/SearchTypeaheadContentScreenshotTest`;
// the variants here cover the collapsed search bar over the Discover body.

@PreviewTest
@Preview(name = "search-screen-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "search-screen-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf(),
                onEvent = {},
                onClearQueryRequest = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-screen-with-chips-light", showBackground = true, heightDp = 600)
@Preview(name = "search-screen-with-chips-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenWithChipsScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf("kotlin", "compose", "room"),
                onEvent = {},
                onClearQueryRequest = {},
            )
        }
    }
}

// Collapsed search bar carrying a query (the trailing clear-X shows when the
// field is non-blank). Phase stays Discover so the body has no Hilt-bound
// result tabs; this isolates the collapsed pill's typed state — the coverage
// the deleted SearchInputRow "typed" baseline used to provide.
@PreviewTest
@Preview(name = "search-screen-query-light", showBackground = true, heightDp = 600)
@Preview(name = "search-screen-query-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenCollapsedWithQueryScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(initialText = "kotlin"),
                isQueryBlank = false,
                currentQuery = "kotlin",
                phase = SearchPhase.Discover,
                recentSearches = persistentListOf(),
                onEvent = {},
                onClearQueryRequest = {},
            )
        }
    }
}
