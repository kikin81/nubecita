package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchPostsError

@PreviewTest
@Preview(name = "posts-error-network-light", showBackground = true)
@Preview(name = "posts-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsInitialErrorBodyNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsInitialErrorBody(error = SearchPostsError.Network, onRetry = {})
        }
    }
}

@PreviewTest
@Preview(name = "posts-error-rate-limited-light", showBackground = true)
@Preview(name = "posts-error-rate-limited-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsInitialErrorBodyRateLimitedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsInitialErrorBody(error = SearchPostsError.RateLimited, onRetry = {})
        }
    }
}

@PreviewTest
@Preview(name = "posts-error-unknown-light", showBackground = true)
@Preview(name = "posts-error-unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostsInitialErrorBodyUnknownScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PostsInitialErrorBody(
                error = SearchPostsError.Unknown(cause = "decode failure"),
                onRetry = {},
            )
        }
    }
}
