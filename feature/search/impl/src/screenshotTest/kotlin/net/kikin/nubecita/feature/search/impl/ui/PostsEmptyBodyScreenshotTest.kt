package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

@PreviewTest
@Preview(name = "posts-empty-top-light", showBackground = true)
@Preview(name = "posts-empty-top-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsEmptyBodyTopScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsEmptyBody(
                currentQuery = "kotlin",
                currentSort = SearchPostsSort.TOP,
                onClearQuery = {},
                onToggleSort = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "posts-empty-latest-light", showBackground = true)
@Preview(name = "posts-empty-latest-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsEmptyBodyLatestScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsEmptyBody(
                currentQuery = "compose",
                currentSort = SearchPostsSort.LATEST,
                onClearQuery = {},
                onToggleSort = {},
            )
        }
    }
}
