package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchActorsError

@PreviewTest
@Preview(name = "people-error-network-light", showBackground = true)
@Preview(name = "people-error-network-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleInitialErrorBodyNetworkScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleInitialErrorBody(error = SearchActorsError.Network, onRetry = {})
        }
    }
}

@PreviewTest
@Preview(name = "people-error-rate-limited-light", showBackground = true)
@Preview(name = "people-error-rate-limited-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleInitialErrorBodyRateLimitedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleInitialErrorBody(error = SearchActorsError.RateLimited, onRetry = {})
        }
    }
}

@PreviewTest
@Preview(name = "people-error-unknown-light", showBackground = true)
@Preview(name = "people-error-unknown-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PeopleInitialErrorBodyUnknownScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            PeopleInitialErrorBody(
                error = SearchActorsError.Unknown(cause = "Decode failure"),
                onRetry = {},
            )
        }
    }
}
