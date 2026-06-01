package net.kikin.nubecita.feature.paywall.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for the post-purchase thank-you screen
 * ([PaywallSuccessContent], nubecita-ykpc).
 *
 * The confetti is animated at runtime, so the baselines pin a **fixed
 * progress frame** (`progressProvider = { 0.4f }`) — deterministic because the
 * particle field is generated from a fixed seed. Two variants × light/dark:
 *
 * - **mid-burst** — `confettiEnabled = true` at progress 0.4: particles spread
 *   from the upper-centre, mid-flight.
 * - **reduce-motion** — `confettiEnabled = false`: the static 🎉 + copy + CTA
 *   with no confetti, pinning the reduce-motion branch.
 */
@PreviewTest
@Preview(name = "paywall-success-light", showBackground = true, heightDp = 720)
@Preview(
    name = "paywall-success-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaywallSuccessMidBurstScreenshot() {
    NubecitaCanvasPreviewTheme {
        PaywallSuccessContent(
            progressProvider = { 0.4f },
            confettiEnabled = true,
            onContinue = {},
        )
    }
}

@PreviewTest
@Preview(name = "paywall-success-reduce-motion-light", showBackground = true, heightDp = 720)
@Preview(
    name = "paywall-success-reduce-motion-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PaywallSuccessReduceMotionScreenshot() {
    NubecitaCanvasPreviewTheme {
        PaywallSuccessContent(
            progressProvider = { 1f },
            confettiEnabled = false,
            onContinue = {},
        )
    }
}
