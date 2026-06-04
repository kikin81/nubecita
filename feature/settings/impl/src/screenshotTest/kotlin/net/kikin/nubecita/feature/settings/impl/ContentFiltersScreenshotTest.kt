package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

@PreviewTest
@Preview(name = "content-filters-adult-off-light", showBackground = true, heightDp = 860)
@Preview(
    name = "content-filters-adult-off-dark",
    showBackground = true,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ContentFiltersAdultOffScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            ContentFiltersContent(
                state = contentFiltersPreviewState(adultEnabled = false),
                onEvent = {},
                onBack = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "content-filters-adult-on-light", showBackground = true, heightDp = 860)
@Preview(
    name = "content-filters-adult-on-dark",
    showBackground = true,
    heightDp = 860,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ContentFiltersAdultOnScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            ContentFiltersContent(
                state = contentFiltersPreviewState(adultEnabled = true),
                onEvent = {},
                onBack = {},
            )
        }
    }
}
