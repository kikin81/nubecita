package net.kikin.nubecita.feature.login.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews

/**
 * Screenshot baselines for [LoginScreen]'s state matrix. Each preview
 * renders the stateless `LoginScreen(state, onEvent)` overload directly
 * (no Hilt) so the captures are deterministic across machines. Run
 * `./gradlew :feature:login:impl:updateDebugScreenshotTest` to regenerate
 * baselines after intentional UI changes; CI runs
 * `validateDebugScreenshotTest` and fails on any unexpected diff.
 */

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(state = LoginState(), onEvent = {})
    }
}

/**
 * Multi-breakpoint adaptive baseline — pins that the login form column
 * is capped at the form max-width and centers horizontally on Medium /
 * Expanded width-class devices instead of stretching edge-to-edge. The
 * empty state is sufficient as the canonical fixture; per-error /
 * per-state coverage stays on the Compact baselines above.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LoginScreenEmptyAdaptivePreviews() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(state = LoginState(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "typed-light", showBackground = true)
@Preview(name = "typed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenTypedScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social"),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "loading-light", showBackground = true)
@Preview(name = "loading-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social", isLoading = true),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "blank-error-light", showBackground = true)
@Preview(name = "blank-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenBlankErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state = LoginState(handle = "", errorMessage = LoginError.BlankHandle),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "handle-not-found-light", showBackground = true)
@Preview(name = "handle-not-found-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenHandleNotFoundScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state =
                LoginState(
                    handle = "alise.bsky.social",
                    errorMessage = LoginError.HandleNotFound("alise.bsky.social"),
                ),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "network-error-light", showBackground = true)
@Preview(name = "network-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenNetworkErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social", errorMessage = LoginError.Network),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "generic-error-light", showBackground = true)
@Preview(name = "generic-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenGenericErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social", errorMessage = LoginError.Generic),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "no-browser-error-light", showBackground = true)
@Preview(name = "no-browser-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenBrowserUnavailableErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        LoginScreen(
            state = LoginState(handle = "alice.bsky.social", errorMessage = LoginError.BrowserUnavailable),
            onEvent = {},
        )
    }
}
