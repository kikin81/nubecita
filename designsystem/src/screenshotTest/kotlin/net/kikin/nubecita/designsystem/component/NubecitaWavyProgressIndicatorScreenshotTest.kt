package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaWavyProgressIndicator] — the brand
 * indeterminate spinner. Captures the default wavy ring in light + dark.
 * Regenerate after intentional visual changes with
 * `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "default-light", showBackground = true)
@Preview(name = "default-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaWavyProgressIndicatorScreenshot() {
    NubecitaWavyProgressIndicator()
}
