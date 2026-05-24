package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaPalette
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaLogomark]. The default-variant case
 * captures light and dark on a brand-sky background so the white logo body
 * is visible in the diff. The tinted-variant case locks the
 * `ColorFilter.tint(...)` fallback used on near-white surfaces. Baselines
 * live under `src/screenshotTestDebug/reference/`. Regenerate after
 * intentional visual changes with
 * `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "logomark-default-light", showBackground = true)
@Preview(name = "logomark-default-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaLogomarkDefaultScreenshot() {
    NubecitaLogomark(modifier = Modifier.size(96.dp).background(NubecitaPalette.Sky50))
}

@PreviewTest
@Preview(name = "logomark-tinted-light", showBackground = true)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaLogomarkTintedScreenshot() {
    NubecitaLogomark(
        modifier = Modifier.size(96.dp).background(NubecitaPalette.Sky99),
        tint = NubecitaPalette.Sky50,
    )
}
