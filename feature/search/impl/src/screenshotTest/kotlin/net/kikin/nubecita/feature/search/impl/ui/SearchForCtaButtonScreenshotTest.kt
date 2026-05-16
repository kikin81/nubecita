package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "search-cta-short-light", showBackground = true)
@Preview(name = "search-cta-short-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchForCtaButtonShortScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchForCtaButton(query = "alice", onClick = {})
        }
    }
}

@PreviewTest
@Preview(name = "search-cta-long-light", showBackground = true)
@Preview(name = "search-cta-long-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchForCtaButtonLongScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchForCtaButton(
                query = "a really long search query that should ellipsize",
                onClick = {},
            )
        }
    }
}
