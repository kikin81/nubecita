package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaScreenPreviewTheme

/**
 * Screenshot baselines for [BlockedPostCard].
 *
 * Two states are visually distinct:
 *
 * - **With Unblock CTA** — the production wiring once oftc.4 lands the
 *   real unblock RPC + optimistic state. Verifies the trailing
 *   `TextButton` color + padding stay aligned with the italic notice.
 * - **Text-only** — the current PostDetail render path (no CTA until
 *   oftc.4). Verifies the row collapses cleanly to "notice only" without
 *   leaving stale padding for the missing button.
 *
 * Each state is captured in light + dark to catch the
 * `surfaceContainer` / `onSurfaceVariant` color pairing across themes.
 */

@PreviewTest
@Preview(name = "with-unblock-light", showBackground = true)
@Preview(
    name = "with-unblock-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BlockedPostCardWithUnblockScreenshot() {
    NubecitaScreenPreviewTheme {
        BlockedPostCard(onUnblock = {})
    }
}

@PreviewTest
@Preview(name = "text-only-light", showBackground = true)
@Preview(
    name = "text-only-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun BlockedPostCardTextOnlyScreenshot() {
    NubecitaScreenPreviewTheme {
        BlockedPostCard(onUnblock = null)
    }
}
