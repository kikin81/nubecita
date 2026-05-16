package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

@PreviewTest
@Preview(name = "posts-sort-row-top-light", showBackground = true)
@Preview(name = "posts-sort-row-top-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsSortRowTopScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsSortRow(activeSort = SearchPostsSort.TOP, onSelectSort = {})
        }
    }
}

@PreviewTest
@Preview(name = "posts-sort-row-latest-light", showBackground = true)
@Preview(name = "posts-sort-row-latest-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsSortRowLatestScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsSortRow(activeSort = SearchPostsSort.LATEST, onSelectSort = {})
        }
    }
}
