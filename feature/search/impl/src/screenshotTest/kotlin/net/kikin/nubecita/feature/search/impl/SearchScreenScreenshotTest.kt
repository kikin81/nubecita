package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

// vrba.8 note: the integrated "non-blank query" variant (TabRow +
// HorizontalPager mounting SearchPostsScreen / SearchActorsScreen) is
// deliberately NOT screenshot-tested here. Both per-tab Screens hoist
// `hiltViewModel<>()` which cannot resolve under the layoutlib-based
// screenshot harness (no Hilt graph). Visual coverage of the tab
// bodies is provided by the per-component baselines in
// `ui/PostsTabContentScreenshotTest` + `ui/PeopleTabContentScreenshotTest`.
// On-device verification covers the integrated swipe + tab-state path.

@PreviewTest
@Preview(name = "search-screen-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "search-screen-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
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
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                currentQuery = "",
                recentSearches = persistentListOf("kotlin", "compose", "room"),
                onEvent = {},
                onClearQueryRequest = {},
            )
        }
    }
}
