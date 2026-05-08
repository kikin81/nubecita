package net.kikin.nubecita.feature.composer.impl

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDialogAction
import net.kikin.nubecita.feature.composer.impl.internal.ComposerDiscardDialogContent

/**
 * Screenshot baselines for the composer discard-confirmation dialog.
 *
 * Layoutlib (which powers Compose Preview / screenshot tests) can't
 * host `BasicAlertDialog` or `Popup` — both require an Activity-hosted
 * Window — so these fixtures render
 * [ComposerDiscardDialogContent] directly. The same primitive ships in
 * production behind both branches of `ComposerDiscardDialog`, so the
 * fixture covers the visible card across both width-class paths.
 *
 * Six fixtures total (Light + Dark × 3 scenarios):
 *
 * 1. **compact-light / compact-dark** — the dialog card at Compact
 *    width (~360dp). Pins the title typography, action-row alignment,
 *    and destructive-tone treatment of the Discard button.
 * 2. **expanded-light / expanded-dark** — the same card at Expanded
 *    width (~840dp). The card itself is naturally narrow (sized by
 *    its content), so this fixture mostly catches regressions in
 *    `Modifier.widthIn(max = ...)` capping.
 * 3. **over-composer-expanded-light / over-composer-expanded-dark** —
 *    bd 8.4 "scrim density" regression check. Renders the discard
 *    card centered over a darkened canvas that simulates the platform
 *    scrim painted by the composer's `Dialog` (the production setup
 *    at Medium / Expanded). Reviewers eyeball that there's exactly
 *    one dim layer behind the card and one tonal-elevated card on
 *    top — no double-scrim.
 *
 * Action lambdas are no-ops in fixtures — the screenshot only captures
 * visual layout, not interaction.
 */

private const val COMPACT_WIDTH_DP: Int = 360
private const val COMPACT_HEIGHT_DP: Int = 480

private const val EXPANDED_WIDTH_DP: Int = 840
private const val EXPANDED_HEIGHT_DP: Int = 720

/**
 * Stand-in for the platform scrim that the composer's Dialog paints
 * at Medium / Expanded widths. Layoutlib doesn't render Dialogs, so
 * the fixture composites the equivalent dim layer directly. Color +
 * alpha mirror M3's `DialogProperties.usePlatformDefaultWidth = false`
 * default scrim.
 */
private val SimulatedComposerScrim: Color = Color.Black.copy(alpha = 0.32f)

@Composable
private fun discardDialogActions(): kotlinx.collections.immutable.ImmutableList<ComposerDialogAction> =
    persistentListOf(
        ComposerDialogAction(
            label = R.string.composer_discard_cancel,
            onClick = {},
        ),
        ComposerDialogAction(
            label = R.string.composer_discard_confirm,
            destructive = true,
            onClick = {},
        ),
    )

@PreviewTest
@Preview(
    name = "discard-dialog-compact-light",
    widthDp = COMPACT_WIDTH_DP,
    heightDp = COMPACT_HEIGHT_DP,
    showBackground = true,
)
@Preview(
    name = "discard-dialog-compact-dark",
    widthDp = COMPACT_WIDTH_DP,
    heightDp = COMPACT_HEIGHT_DP,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComposerDiscardDialogCompactScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ComposerDiscardDialogContent(actions = discardDialogActions())
        }
    }
}

@PreviewTest
@Preview(
    name = "discard-dialog-expanded-light",
    widthDp = EXPANDED_WIDTH_DP,
    heightDp = EXPANDED_HEIGHT_DP,
    showBackground = true,
)
@Preview(
    name = "discard-dialog-expanded-dark",
    widthDp = EXPANDED_WIDTH_DP,
    heightDp = EXPANDED_HEIGHT_DP,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComposerDiscardDialogExpandedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Narrow the dialog explicitly so the Expanded fixture
            // doesn't stretch the card to the full canvas — production
            // sizes it by content, but in a fixture the Box parent has
            // no other competing children to push back against.
            ComposerDiscardDialogContent(
                actions = discardDialogActions(),
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}

@PreviewTest
@Preview(
    name = "discard-dialog-over-composer-expanded-light",
    widthDp = EXPANDED_WIDTH_DP,
    heightDp = EXPANDED_HEIGHT_DP,
    showBackground = true,
)
@Preview(
    name = "discard-dialog-over-composer-expanded-dark",
    widthDp = EXPANDED_WIDTH_DP,
    heightDp = EXPANDED_HEIGHT_DP,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ComposerDiscardDialogOverComposerExpandedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(SimulatedComposerScrim),
            contentAlignment = Alignment.Center,
        ) {
            ComposerDiscardDialogContent(
                actions = discardDialogActions(),
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
    }
}
