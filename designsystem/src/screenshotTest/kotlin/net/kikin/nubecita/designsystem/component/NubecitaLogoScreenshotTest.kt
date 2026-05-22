package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [NubecitaLogomark]. Captures both light and dark
 * via stacked `@Preview` annotations on the brand sky background so the
 * white logomark is actually visible in the diff. Baselines live under
 * `src/screenshotTestDebug/reference/`. Regenerate after intentional visual
 * changes with `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "logomark-default-light", showBackground = true)
@Preview(name = "logomark-default-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NubecitaLogomarkDefaultScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(modifier = Modifier.size(96.dp).background(Color(0xFF0A7AFF)))
    }
}
