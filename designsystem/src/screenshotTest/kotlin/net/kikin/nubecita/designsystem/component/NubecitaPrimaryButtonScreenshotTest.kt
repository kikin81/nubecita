package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaPrimaryButton]. Each preview function
 * captures a state in both light + dark via stacked `@Preview` annotations
 * on a single `@PreviewTest`-marked function. Baselines live under
 * `src/screenshotTestDebug/reference/`. Regenerate after intentional
 * visual changes with
 * `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "idle-light", showBackground = true)
@Preview(name = "idle-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaPrimaryButtonIdleScreenshot() {
    NubecitaPrimaryButton(onClick = {}, text = "Continue")
}

@PreviewTest
@Preview(name = "loading-light", showBackground = true)
@Preview(name = "loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaPrimaryButtonLoadingScreenshot() {
    NubecitaPrimaryButton(onClick = {}, text = "Continue", isLoading = true)
}

@PreviewTest
@Preview(name = "disabled-light", showBackground = true)
@Preview(name = "disabled-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaPrimaryButtonDisabledScreenshot() {
    NubecitaPrimaryButton(onClick = {}, text = "Continue", enabled = false)
}
