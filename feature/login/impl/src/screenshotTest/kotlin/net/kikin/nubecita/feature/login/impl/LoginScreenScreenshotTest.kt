package net.kikin.nubecita.feature.login.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

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
    NubecitaTheme(dynamicColor = false) {
        LoginScreen(state = LoginState(), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "typed-light", showBackground = true)
@Preview(name = "typed-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenTypedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
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
    NubecitaTheme(dynamicColor = false) {
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
    NubecitaTheme(dynamicColor = false) {
        LoginScreen(
            state = LoginState(handle = "", errorMessage = LoginError.BlankHandle),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "failure-error-light", showBackground = true)
@Preview(name = "failure-error-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginScreenFailureErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        LoginScreen(
            state =
                LoginState(
                    handle = "alice",
                    errorMessage = LoginError.Failure("Handle could not be resolved."),
                ),
            onEvent = {},
        )
    }
}
