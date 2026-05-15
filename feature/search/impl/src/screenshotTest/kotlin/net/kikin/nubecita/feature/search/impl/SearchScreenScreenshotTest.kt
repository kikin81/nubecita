package net.kikin.nubecita.feature.search.impl

import android.content.res.Configuration
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

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
                recentSearches = persistentListOf(),
                onSubmit = {},
                onChipTap = {},
                onChipRemove = {},
                onClearAll = {},
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
                recentSearches = persistentListOf("kotlin", "compose", "room"),
                onSubmit = {},
                onChipTap = {},
                onChipRemove = {},
                onClearAll = {},
            )
        }
    }
}
