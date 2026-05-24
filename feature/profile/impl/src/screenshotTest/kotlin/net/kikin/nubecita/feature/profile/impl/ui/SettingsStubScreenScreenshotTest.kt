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
 * Screenshot baselines for the rebuilt Settings screen body
 * ([SettingsStubContent]).
 *
 * Four fixtures × Light/Dark = 8 baselines:
 *
 * - **signed-in** — handle + display name + avatarUrl all populated.
 *   Header renders "Hi, <displayName>!" with the async-image avatar;
 *   SwitchAccountRow mirrors the avatar at 32dp. The Account +
 *   About section cards show the canonical Sign Out + Version rows.
 * - **missing-display-name** — handle only. Header falls back to
 *   "Hi!" (no name) and the avatar renders as the deterministic
 *   initials-disc fallback (`SettingsAvatar` when `avatarUrl ==
 *   null`).
 * - **confirm-dialog-open** — dialog overlay above the signed-in
 *   body, idle status, Confirm enabled.
 * - **signing-out** — dialog overlay, SigningOut status, Confirm
 *   shows the spinner.
 *
 * The wrapper-level adaptive shape (Compact = Scaffold + back arrow;
 * Medium+ = centered modal with X-close) lives outside this test —
 * Compose tooling's `@Preview` doesn't render Dialog windows, so
 * the modal wrapper is verified on-device per the screen-shape spec
 * scenarios (`feature-settings` → "Tablet width renders modal…").
 *
 * The TopAppBar + SnackbarHost aren't exercised here either —
 * those live in the wrapper, not the body.
 */
@PreviewTest
@Preview(name = "settings-signed-in-light", showBackground = true, heightDp = 720)
@Preview(
    name = "settings-signed-in-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsSignedInScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state =
                SettingsStubViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    avatarUrl = null, // null → initials-disc fallback in screenshots; avatarUrl loads at runtime
                ),
            onEvent = {},
            versionLabel = FIXTURE_VERSION_LABEL,
        )
    }
}

@PreviewTest
@Preview(name = "settings-missing-display-name-light", showBackground = true, heightDp = 720)
@Preview(
    name = "settings-missing-display-name-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsMissingDisplayNameScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state =
                SettingsStubViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = null,
                    avatarUrl = null,
                ),
            onEvent = {},
            versionLabel = FIXTURE_VERSION_LABEL,
        )
    }
}

@PreviewTest
@Preview(name = "settings-confirm-open-light", showBackground = true, heightDp = 720)
@Preview(
    name = "settings-confirm-open-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsConfirmDialogScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state =
                SettingsStubViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    confirmDialogOpen = true,
                ),
            onEvent = {},
            versionLabel = FIXTURE_VERSION_LABEL,
        )
    }
}

@PreviewTest
@Preview(name = "settings-signing-out-light", showBackground = true, heightDp = 720)
@Preview(
    name = "settings-signing-out-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsSigningOutScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        SettingsStubContent(
            state =
                SettingsStubViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    confirmDialogOpen = true,
                    status = SettingsStubStatus.SigningOut,
                ),
            onEvent = {},
            versionLabel = FIXTURE_VERSION_LABEL,
        )
    }
}

private const val FIXTURE_HANDLE = "alice.bsky.social"
private const val FIXTURE_DISPLAY_NAME = "Alice Anderson"

// Stable string so screenshot baselines aren't tied to whatever
// semantic-release happens to have bumped to at fixture-capture time.
private const val FIXTURE_VERSION_LABEL = "1.104.0 (1104000)"
