package net.kikin.nubecita.designsystem.preview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

/**
 * Pins the canvas paint produced by [NubecitaScreenPreviewTheme] in both
 * light and dark mode. A transparent content slice (`Text` only) on top
 * of the wrapper proves the wrapper itself owns the canvas — if the
 * wrapper regresses (loses `fillMaxSize()`, drops the `Surface`, etc.),
 * one or both of these baselines will diff and CI will catch it.
 */
@PreviewTest
@Preview(name = "screen-preview-theme-light", showBackground = true)
@Preview(
    name = "screen-preview-theme-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NubecitaScreenPreviewThemeCanvasScreenshot() {
    NubecitaScreenPreviewTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("canvas")
        }
    }
}
