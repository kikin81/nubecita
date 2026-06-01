package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.BillingPeriod
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for the Settings screen body ([SettingsContent]).
 *
 * Four fixtures × Light/Dark = 8 baselines:
 *
 * - **signed-in** — handle + display name populated; `avatarUrl =
 *   null` so the deterministic initials-disc fallback renders
 *   (with the displayName-derived initial). The async-image branch
 *   exercises at runtime; the screenshot pins the fallback path so
 *   baselines aren't dependent on Coil / network state.
 * - **missing-display-name** — handle only. Header falls back to
 *   "Hi!" (no name) and the avatar renders as the initials disc
 *   seeded from the handle's first letter.
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
    NubecitaCanvasPreviewTheme {
        SettingsContent(
            state =
                SettingsViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    avatarUrl = null, // pins the initials-disc fallback path so baselines aren't network-dependent
                    avatarHue = FIXTURE_AVATAR_HUE,
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
    NubecitaCanvasPreviewTheme {
        SettingsContent(
            state =
                SettingsViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = null,
                    avatarUrl = null,
                    avatarHue = FIXTURE_AVATAR_HUE,
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
    NubecitaCanvasPreviewTheme {
        SettingsContent(
            state =
                SettingsViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    avatarHue = FIXTURE_AVATAR_HUE,
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
    NubecitaCanvasPreviewTheme {
        SettingsContent(
            state =
                SettingsViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    avatarHue = FIXTURE_AVATAR_HUE,
                    confirmDialogOpen = true,
                    status = SettingsStatus.SigningOut,
                ),
            onEvent = {},
            versionLabel = FIXTURE_VERSION_LABEL,
        )
    }
}

@PreviewTest
@Preview(name = "settings-pro-member-light", showBackground = true, heightDp = 720)
@Preview(
    name = "settings-pro-member-dark",
    showBackground = true,
    heightDp = 720,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SettingsProMemberScreenshot() {
    // Pro face of the "Nubecita Pro" section: current-plan info row (annual,
    // price resolved) + manage-subscription + restore rows. The non-Pro upsell
    // face is already covered by the signed-in fixtures above (isPro defaults
    // to false), so this pins only the Pro variant.
    NubecitaCanvasPreviewTheme {
        SettingsContent(
            state =
                SettingsViewState(
                    handle = FIXTURE_HANDLE,
                    displayName = FIXTURE_DISPLAY_NAME,
                    avatarUrl = null,
                    avatarHue = FIXTURE_AVATAR_HUE,
                    isPro = true,
                    currentPlanPeriod = BillingPeriod.Annual,
                    currentPlanFormattedPrice = "$19.99",
                    manageSku = "pro_sub:annual",
                ),
            onEvent = {},
            versionLabel = FIXTURE_VERSION_LABEL,
        )
    }
}

private const val FIXTURE_HANDLE = "alice.bsky.social"
private const val FIXTURE_DISPLAY_NAME = "Alice Anderson"

// Stable hue for the deterministic-initials avatar disc — matches the
// canonical Alice fixture used across ProfileTopBarScreenshotTest etc.
// Keeps the baseline color identical across runs without computing
// avatarHueFor(did, handle) at fixture time.
private const val FIXTURE_AVATAR_HUE = 217

// Stable string so screenshot baselines aren't tied to whatever
// semantic-release happens to have bumped to at fixture-capture time.
private const val FIXTURE_VERSION_LABEL = "1.104.0 (1104000)"
