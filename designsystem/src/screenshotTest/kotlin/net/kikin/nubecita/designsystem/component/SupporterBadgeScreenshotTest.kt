package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [SupporterBadge] — the gold Pro "Supporter"
 * pill. Light + dark pin the warm-gold fill and the fixed-dark content
 * (medal glyph + label) in both schemes, catching glyph-metrics drift on
 * the freshly-subset `WorkspacePremium` codepoint and any accent-token
 * regression. Regenerate with
 * `./gradlew :designsystem:updateDebugScreenshotTest` after intentional
 * visual changes.
 */
@PreviewTest
@Preview(name = "supporter-badge-light", showBackground = true)
@Preview(name = "supporter-badge-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SupporterBadgeScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SupporterBadge()
        }
    }
}
