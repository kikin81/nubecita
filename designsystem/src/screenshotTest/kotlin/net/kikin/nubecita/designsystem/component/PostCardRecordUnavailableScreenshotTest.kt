package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardRecordUnavailable]. v1 renders
 * the same copy regardless of [Reason] (single-stub design), so a
 * single Reason × {light, dark} pair is enough for screenshot
 * coverage.
 *
 * The "all four Reasons converge to identical output" contract is
 * enforced at two layers, neither of which is a UI rendering test:
 *
 * - **Source-level guarantee** — the composable's `reason`
 *   parameter is `@Suppress("UNUSED_PARAMETER")`'d and never read
 *   in the body, so the rendered output structurally cannot vary
 *   by `Reason`.
 * - **Enum-shape pin in `PostUiTest`** — a separate JVM unit test
 *   asserts `Reason.values()` is exactly
 *   `[NotFound, Blocked, Detached, Unknown]` in stable order. A
 *   future change that adds a 5th `Reason` breaks that test and
 *   forces a deliberate decision about whether the new variant
 *   needs differentiated copy.
 *
 * Adding a Compose UI rendering test for "render the composable
 * four times with each Reason and assert identical Text content"
 * would require wiring `runComposeUiTest` / Robolectric into
 * `:designsystem` for marginal additional coverage; deferred until
 * a real UI-test need arrives.
 */

@PreviewTest
@Preview(name = "light", showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardRecordUnavailableScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardRecordUnavailable(reason = EmbedUi.RecordUnavailable.Reason.NotFound)
    }
}
