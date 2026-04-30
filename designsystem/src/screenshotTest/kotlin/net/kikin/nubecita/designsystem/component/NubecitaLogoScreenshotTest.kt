package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [NubecitaLogomark]. Each preview captures a
 * tint variant in light + dark via stacked `@Preview` annotations. Baselines
 * live under `src/screenshotTestDebug/reference/`. Regenerate after
 * intentional visual changes with
 * `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "logomark-default-light", showBackground = true)
@Preview(name = "logomark-default-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NubecitaLogomarkDefaultScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(modifier = Modifier.size(96.dp))
    }
}

@PreviewTest
@Preview(name = "logomark-custom-tint-light", showBackground = true)
@Composable
private fun NubecitaLogomarkCustomTintScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        NubecitaLogomark(
            modifier = Modifier.size(96.dp),
            tint = Color(0xFF0A7AFF),
        )
    }
}
