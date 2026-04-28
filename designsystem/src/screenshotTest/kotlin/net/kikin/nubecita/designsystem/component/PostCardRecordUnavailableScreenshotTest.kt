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
 * single Reason × {light, dark} pair is enough — the unit-test
 * suite covers the "all four Reasons render identically" invariant
 * that backs that decision.
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
