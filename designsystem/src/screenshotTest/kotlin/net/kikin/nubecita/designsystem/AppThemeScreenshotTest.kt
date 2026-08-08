package net.kikin.nubecita.designsystem

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.ColorRoster

/**
 * Visual baselines for the `NubecitaTheme(appTheme = …)` overload
 * (`openspec/changes/add-theme-selection`, task 2.4).
 *
 * [ColorRoster] is the fixture because it paints the M3 color roles directly,
 * so a mis-resolved scheme shows up as a wholesale palette change rather than
 * a subtle tint somewhere in a component.
 *
 * The pairs that matter here are the *forced* ones: [AppTheme.Light] rendered
 * under a dark OS and [AppTheme.Dark] rendered under a light OS. Those are the
 * two cases the whole feature exists for, and each must ignore the `uiMode` it
 * is rendered with. A regression that dropped the override would collapse each
 * onto its OS-matching sibling, which the committed baselines then catch.
 */
@PreviewTest
@Preview(name = "Dark theme, light OS", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun AppThemeDarkOnLightOsPreview() {
    NubecitaTheme(appTheme = AppTheme.Dark) {
        Surface { ColorRoster() }
    }
}

@PreviewTest
@Preview(name = "Light theme, dark OS", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppThemeLightOnDarkOsPreview() {
    NubecitaTheme(appTheme = AppTheme.Light) {
        Surface { ColorRoster() }
    }
}

/**
 * [AppTheme.Dynamic] is the one option that defers to the OS, so it gets both
 * `uiMode` variants — together they pin that it tracks the system setting
 * rather than sitting on a fixed brightness.
 */
@PreviewTest
@Preview(name = "Dynamic theme, light OS", uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun AppThemeDynamicOnLightOsPreview() {
    NubecitaTheme(appTheme = AppTheme.Dynamic) {
        Surface { ColorRoster() }
    }
}

@PreviewTest
@Preview(name = "Dynamic theme, dark OS", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppThemeDynamicOnDarkOsPreview() {
    NubecitaTheme(appTheme = AppTheme.Dynamic) {
        Surface { ColorRoster() }
    }
}
