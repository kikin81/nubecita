package net.kikin.nubecita.designsystem.hero

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.BoldHeroGradientCatalog
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews

/**
 * Visual baseline for [BoldHeroGradient] across the catalog of
 * avatar-hue fallback gradients. The banner-present path needs a
 * live Coil pipeline and is exercised in instrumentation tests +
 * real-device verification (Bead D); this fixture covers the
 * deterministic `banner = null` path that all profiles fall back
 * to before extraction completes.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun BoldHeroGradientScreenshotPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            BoldHeroGradientCatalog()
        }
    }
}
