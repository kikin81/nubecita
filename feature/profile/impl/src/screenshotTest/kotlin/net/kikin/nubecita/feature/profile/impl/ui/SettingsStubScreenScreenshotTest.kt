package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.profile.impl.SettingsStubContent
import net.kikin.nubecita.feature.profile.impl.SettingsStubStatus
import net.kikin.nubecita.feature.profile.impl.SettingsStubViewState

/**
 * Screenshot baselines for the Settings stub screen. Three states ×
 * two themes = 6 baselines. The TopAppBar isn't exercised here
 * (separate from the body's lifecycle); the snackbar host is empty.
 *
 * - idle: no dialog, Sign Out button enabled
 * - confirm-open: dialog visible, idle status, Confirm enabled
 * - signing-out: dialog visible, SigningOut status, Confirm shows spinner
 */
@PreviewTest
@Preview(name = "settings-idle-light", showBackground = true, heightDp = 600)
@Preview(
    name = "settings-idle-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsStubIdleScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(state = SettingsStubViewState(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "settings-confirm-open-light", showBackground = true, heightDp = 600)
@Preview(
    name = "settings-confirm-open-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsStubConfirmOpenScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state = SettingsStubViewState(confirmDialogOpen = true),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "settings-signing-out-light", showBackground = true, heightDp = 600)
@Preview(
    name = "settings-signing-out-dark",
    showBackground = true,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsStubSigningOutScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state =
                SettingsStubViewState(
                    confirmDialogOpen = true,
                    status = SettingsStubStatus.SigningOut,
                ),
            onEvent = {},
        )
    }
}
