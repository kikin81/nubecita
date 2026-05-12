package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [ProfileActionsRow]. Bead D pins only the
 * own-variant (Edit + overflow); Bead F adds the other-user variant
 * (Follow + Message + overflow) and the dropdown menu wired to the
 * overflow icon. Regenerate with
 * `./gradlew :feature:profile:impl:updateDebugScreenshotTest` after
 * intentional visual changes.
 */
@PreviewTest
@Preview(name = "actions-own-light", showBackground = true)
@Preview(name = "actions-own-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProfileActionsRowOwnScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ProfileActionsRow(ownProfile = true, onEdit = {}, onOverflow = {})
    }
}
