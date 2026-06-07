package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview

/**
 * Screenshot baselines for [NubecitaPullToRefreshBox]. The refreshing
 * fixture passes a non-zero [indicatorPadding] over a filled background so
 * the baseline locks the M3 Expressive LoadingIndicator's offset below a
 * (simulated) top bar — the regression guard for the indicator rendering
 * behind a `topBar` (nubecita-tfbc). Baselines live under
 * `src/screenshotTestDebug/reference/`. Regenerate after intentional visual
 * changes with `./gradlew :designsystem:updateDebugScreenshotTest`.
 */

@PreviewTest
@Preview(name = "pulled-offset-light", showBackground = true)
@Preview(name = "pulled-offset-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaPullToRefreshBoxPulledScreenshot() {
    NubecitaPullToRefreshBox(
        isRefreshing = false,
        onRefresh = {},
        modifier = Modifier.size(width = 240.dp, height = 220.dp),
        state = fullyPulledState,
        // Simulated topBar inset: the indicator must clear this, not sit at y=0.
        indicatorPadding = PaddingValues(top = 72.dp),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            item { Text(text = "Pull-to-refresh content") }
        }
    }
}

// A fully-pulled, non-animating state stub. The real M3 indicator only becomes
// visible once a gesture/refresh drives the state past the threshold via suspend
// animations — which the static screenshot host never runs, leaving a fresh
// `isRefreshing = true` indicator hidden. Pinning distanceFraction = 1f renders
// the determinate indicator at full pull so the baseline locks its position
// (offset below the simulated topBar).
private val fullyPulledState =
    object : PullToRefreshState {
        override val distanceFraction: Float = 1f
        override val isAnimating: Boolean = false

        override suspend fun animateToThreshold() = Unit

        override suspend fun animateToHidden() = Unit

        override suspend fun snapTo(targetValue: Float) = Unit
    }

@PreviewTest
@Preview(name = "idle-light", showBackground = true)
@Preview(name = "idle-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun NubecitaPullToRefreshBoxIdleScreenshot() {
    NubecitaPullToRefreshBox(
        isRefreshing = false,
        onRefresh = {},
        modifier = Modifier.size(width = 240.dp, height = 220.dp),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
            item { Text(text = "Pull-to-refresh content") }
        }
    }
}
