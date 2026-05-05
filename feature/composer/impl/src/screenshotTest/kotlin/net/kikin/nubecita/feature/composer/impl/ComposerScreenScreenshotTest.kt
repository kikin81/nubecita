package net.kikin.nubecita.feature.composer.impl

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.posting.ComposerAttachment
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
 *   close-disabled tonal swap that ships as part of this task.
 * - **With-images composer** (3 attachments, short text, idle).
 *   Fake content URIs that don't resolve, so each chip renders the
 *   `NubecitaAsyncImage` placeholder ColorPainter — same deterministic
 *   render as the design-system's own AsyncImage placeholder fixture.
 *   Locks chip sizing, spacing, the leading "Add image" affordance's
 *   visibility while still under the cap, and the per-chip remove-
 *   button overlay tonality.
 *
 * Each fixture renders in Light + Dark = 8 baselines total.
 *
 * Reply-mode fixtures and Expanded-Dialog fixtures land in
 * `nubecita-wtq.6` and `nubecita-wtq.7` respectively as those code
 * paths come online.
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

@PreviewTest
@Preview(name = "with-images-light", showBackground = true)
@Preview(name = "with-images-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComposerScreenWithImagesScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ComposerScreenContent(
            state =
                ComposerState(
                    text = "Three placeholder attachments",
                    graphemeCount = 29,
                    attachments =
                        persistentListOf(
                            ComposerAttachment(uri = Uri.parse("content://fixture/0"), mimeType = "image/jpeg"),
                            ComposerAttachment(uri = Uri.parse("content://fixture/1"), mimeType = "image/jpeg"),
                            ComposerAttachment(uri = Uri.parse("content://fixture/2"), mimeType = "image/jpeg"),
                        ),
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
