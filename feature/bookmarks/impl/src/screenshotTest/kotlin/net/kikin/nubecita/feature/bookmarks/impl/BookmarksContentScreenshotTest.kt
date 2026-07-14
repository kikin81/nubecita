package net.kikin.nubecita.feature.bookmarks.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.bookmarks.impl.ui.BookmarksContent

/**
 * Screenshot coverage for the Bookmarks list's deterministic load states
 * (loading / empty / error). The Loaded state renders PostCards whose
 * relative-time labels depend on the wall clock, so it is covered by the
 * VM unit tests + the shared PostCard screenshots in :designsystem instead
 * of re-pinned here.
 */
@PreviewTest
@Preview(name = "bookmarks-loading-light", showBackground = true)
@Preview(name = "bookmarks-loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            BookmarksContent(
                state = BookmarksState(BookmarksLoadStatus.InitialLoading),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "bookmarks-empty-light", showBackground = true)
@Preview(name = "bookmarks-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            BookmarksContent(
                state = BookmarksState(BookmarksLoadStatus.Empty),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "bookmarks-error-network-light", showBackground = true)
@Preview(name = "bookmarks-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksErrorNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            BookmarksContent(
                state = BookmarksState(BookmarksLoadStatus.InitialError(BookmarksError.Network)),
                onEvent = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "bookmarks-error-unknown-light", showBackground = true)
@Preview(name = "bookmarks-error-unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksErrorUnknownScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            BookmarksContent(
                state = BookmarksState(BookmarksLoadStatus.InitialError(BookmarksError.Unknown(cause = "boom"))),
                onEvent = {},
            )
        }
    }
}
