package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [ProfileMetaRow]. The "all" variant pins the
 * three-row layout (website + location + joined) and the "joined-only"
 * variant pins the per-row conditional rendering (link + location are
 * hidden when null per the spec scenario "Meta row hides absent
 * optional fields"). Regenerate with
 * `./gradlew :feature:profile:impl:updateDebugScreenshotTest` after
 * intentional visual changes.
 */
@PreviewTest
@Preview(name = "meta-all-light", showBackground = true)
@Preview(name = "meta-all-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileMetaRowAllScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileMetaRow(
            website = "alice.example.com",
            location = "Lima, Peru",
            joinedDisplay = "Joined April 2023",
        )
    }
}

@PreviewTest
@Preview(name = "meta-joined-only-light", showBackground = true)
@Preview(
    name = "meta-joined-only-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ProfileMetaRowJoinedOnlyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileMetaRow(
            website = null,
            location = null,
            joinedDisplay = "Joined April 2023",
        )
    }
}
