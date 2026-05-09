package net.kikin.nubecita.shell.composer

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.composer.impl.ComposerScreenContent
import net.kikin.nubecita.feature.composer.impl.state.ComposerState

/**
 * Screenshot baselines for the composer overlay's centered-card
 * visual treatment at Medium / Expanded widths.
 *
 * Production [ComposerOverlay] wraps [ComposerOverlayCard] in a real
 * Compose [androidx.compose.ui.window.Dialog]. Layoutlib (used by
 * screenshot tests) can't render Dialogs because they require an
 * Activity-hosted window, so this test renders [ComposerOverlayCard]
 * directly inside a Box with a darkened scrim simulating the Dialog's
 * platform-rendered scrim. The visual primitive — the centered M3
 * Surface capped at 640dp wide — is shared verbatim between
 * production and the fixture.
 *
 * The fixture renders at 840dp width (Expanded tier) so the 640dp
 * cap is visible (the Surface should not stretch to the full canvas).
 *
 * Per the wtq.7 spec — 2 new fixtures (Light + Dark = total 14
 * after wtq.6's reply-mode pair landed at 12).
 */

private const val EXPANDED_WIDTH_DP: Int = 840
private const val EXPANDED_HEIGHT_DP: Int = 1024

@PreviewTest
@Preview(
    name = "composer-overlay-empty-expanded-light",
    widthDp = EXPANDED_WIDTH_DP,
    heightDp = EXPANDED_HEIGHT_DP,
)
@Preview(
    name = "composer-overlay-empty-expanded-dark",
    widthDp = EXPANDED_WIDTH_DP,
    heightDp = EXPANDED_HEIGHT_DP,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComposerOverlayEmptyExpandedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    // Darkened scrim matching production [ComposerOverlay]'s
                    // manual scrim. Compose's Dialog with
                    // `usePlatformDefaultWidth = false` sets the window
                    // to `MATCH_PARENT` and the platform dim layer is
                    // suppressed, so the production Dialog content
                    // paints its own scrim and so does this fixture.
                    .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            // Production [ComposerOverlay] sizes the card via
            // [BoxWithConstraints] inside the Dialog (cap at 640dp,
            // shrinks to maxWidth on narrower windows). The fixture
            // mirrors that with an explicit `width(640.dp)` Box,
            // since renders here at 840dp Expanded canvas where the
            // cap engages.
            Box(
                modifier =
                    Modifier
                        .width(640.dp)
                        .fillMaxHeight(),
            ) {
                ComposerOverlayCard {
                    ComposerScreenContent(
                        state = ComposerState(),
                        textFieldState = remember { TextFieldState() },
                        snackbarHostState = remember { SnackbarHostState() },
                        deviceLocaleTag = "en-US",
                        onSubmit = {},
                        onCloseClick = {},
                        onAddImageClick = {},
                        onRemoveAttachment = {},
                        onSuggestionClick = {},
                        onRetryParentLoad = {},
                        onLanguageChipClick = {},
                    )
                }
            }
        }
    }
}
