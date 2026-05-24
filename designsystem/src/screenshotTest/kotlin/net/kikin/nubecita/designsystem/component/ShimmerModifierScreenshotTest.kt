package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baseline for [Modifier.shimmer]. Captures a single frame
 * of the animated brush — the test runner snapshots at composition time,
 * so the brush is at its initial translate position. If the animation's
 * starting frame visibly drifts across runs, regenerate the baseline
 * with `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "modifier-light", showBackground = true)
@Preview(name = "modifier-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ShimmerModifierScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(modifier = Modifier.size(40.dp).shimmer())
            Box(
                modifier =
                    Modifier
                        .width(120.dp)
                        .height(40.dp)
                        .shimmer(),
            )
        }
    }
}
