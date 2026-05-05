package net.kikin.nubecita.feature.composer.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus

/**
 * Screenshot baselines for [ComposerScreenContent]'s V1 visual
 * states. Renders against fixture state — no Hilt, no ViewModel.
 *
 * V1 fixtures (per the spec's screenshot-test contract):
 *
 * - **Empty composer** (new-post mode, no text, no attachments, idle)
 * - **Near-limit composer** (new-post mode, `graphemeCount` pinned to
 *   295 — inside the error band (≥ 290) but still below the 300-char
 *   hard limit, so the counter renders in the error tone while the
 *   input border stays at the standard tone — the screen has not yet
 *   tipped into the over-limit state)
 * - **Submitting composer** (new-post mode, mid-submission — Post
 *   button morphs to inline circular progress, close button gates
 *   off while in flight, the text field is disabled). Catches
 *   regressions in spinner sizing/color, Post-button morph, and the
 *   close-disabled tonal swap that ship as part of this task.
 *
 * Each fixture renders in Light + Dark = 6 baselines total.
 *
 * Reply-mode fixtures, with-images fixtures, and Expanded-Dialog
 * fixtures land in `nubecita-wtq.5` / `.6` / `.7` respectively as
 * those code paths come online.
 */

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComposerScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ComposerScreenContent(
            state = ComposerState(),
            snackbarHostState = remember { SnackbarHostState() },
            onTextChange = {},
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
        )
    }
}

@PreviewTest
@Preview(name = "near-limit-light", showBackground = true)
@Preview(name = "near-limit-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComposerScreenNearLimitScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ComposerScreenContent(
            state =
                ComposerState(
                    text = "x".repeat(295),
                    graphemeCount = 295,
                    isOverLimit = false,
                    submitStatus = ComposerSubmitStatus.Idle,
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onTextChange = {},
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
        )
    }
}

@PreviewTest
@Preview(name = "submitting-light", showBackground = true)
@Preview(name = "submitting-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComposerScreenSubmittingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ComposerScreenContent(
            state =
                ComposerState(
                    text = "Hello, Bluesky.",
                    graphemeCount = 15,
                    isOverLimit = false,
                    submitStatus = ComposerSubmitStatus.Submitting,
                ),
            snackbarHostState = remember { SnackbarHostState() },
            onTextChange = {},
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
        )
    }
}
