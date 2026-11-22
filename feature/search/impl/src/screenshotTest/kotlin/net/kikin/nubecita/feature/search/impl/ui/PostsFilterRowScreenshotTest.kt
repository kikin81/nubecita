package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.data.MediaFilter
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

private val followingAndLanguage = PostSearchFilters(followingOnly = true, language = "es")

@PreviewTest
@Preview(name = "posts-filter-row-none-light", showBackground = true)
@Preview(name = "posts-filter-row-none-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsFilterRowNoFiltersScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsFilterRow(filters = PostSearchFilters(), onFiltersChange = {}, onOpenSheet = {})
        }
    }
}

@PreviewTest
@Preview(name = "posts-filter-row-following-language-light", showBackground = true)
@Preview(name = "posts-filter-row-following-language-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsFilterRowFollowingAndLanguageScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsFilterRow(filters = followingAndLanguage, onFiltersChange = {}, onOpenSheet = {})
        }
    }
}

/** Long es-419 labels: the row scrolls and no chip truncates its label. */
@PreviewTest
@Preview(name = "posts-filter-row-following-language-es419", showBackground = true, locale = "b+es+419")
@Composable
private fun PostsFilterRowFollowingAndLanguageEs419Screenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsFilterRow(filters = followingAndLanguage, onFiltersChange = {}, onOpenSheet = {})
        }
    }
}

@PreviewTest
@Preview(name = "posts-filter-row-video-latest-pt-br", showBackground = true, locale = "pt-rBR")
@Composable
private fun PostsFilterRowVideoLatestPtBrScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsFilterRow(
                filters = PostSearchFilters(sort = SearchPostsSort.LATEST, media = MediaFilter.VIDEO_ONLY),
                onFiltersChange = {},
                onOpenSheet = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "posts-filter-header-with-count-light", showBackground = true)
@Preview(name = "posts-filter-header-with-count-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsFilterHeaderWithCountScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsFilterHeader(
                filters = followingAndLanguage,
                hitsTotal = 1_240L,
                onFiltersChange = {},
                onOpenSheet = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "posts-filter-header-no-count-light", showBackground = true)
@Composable
private fun PostsFilterHeaderWithoutCountScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsFilterHeader(
                filters = followingAndLanguage,
                hitsTotal = null,
                onFiltersChange = {},
                onOpenSheet = {},
            )
        }
    }
}
