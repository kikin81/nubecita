package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [OwnProfileActionsRow]. Covers the closed
 * state (overflow menu collapsed); the menu-open state is intentionally
 * NOT screenshotted because [`androidx.compose.material3.DropdownMenu`]
 * renders inside an overlay window that Layoutlib doesn't compose
 * deterministically. The open-state coverage lives in the
 * instrumentation test instead.
 */
@PreviewTest
@Preview(name = "own-edit-overflow-closed-light", showBackground = true, heightDp = 80)
@Preview(
    name = "own-edit-overflow-closed-dark",
    showBackground = true,
    heightDp = 80,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OwnProfileActionsRowScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        OwnProfileActionsRow(onEdit = {}, onSettings = {})
    }
}
