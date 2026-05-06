package net.kikin.nubecita.feature.composer.impl

import android.content.res.Configuration
import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.composer.impl.state.ComposerState
import net.kikin.nubecita.feature.composer.impl.state.ComposerSubmitStatus
import net.kikin.nubecita.feature.composer.impl.state.TypeaheadStatus

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
 *
 * **Text fixtures construct `TextFieldState` locally** rather than
 * pulling text off `ComposerState`. The composer's text owner-ship
 * lives in `ComposerViewModel.textFieldState` (see that class's
 * Kdoc); fixtures replicate the wiring by `remember { TextFieldState(initialText = …) }`.
 */

@PreviewTest
@Preview(name = "empty-light", showBackground = true)
@Preview(name = "empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComposerScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ComposerScreenContent(
            state = ComposerState(),
            textFieldState = remember { TextFieldState() },
            snackbarHostState = remember { SnackbarHostState() },
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
            onSuggestionClick = {},
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
                    graphemeCount = 295,
                    isOverLimit = false,
                    submitStatus = ComposerSubmitStatus.Idle,
                ),
            textFieldState = remember { TextFieldState(initialText = "x".repeat(295)) },
            snackbarHostState = remember { SnackbarHostState() },
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
            onSuggestionClick = {},
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
                    graphemeCount = 15,
                    isOverLimit = false,
                    submitStatus = ComposerSubmitStatus.Submitting,
                ),
            textFieldState = remember { TextFieldState(initialText = "Hello, Bluesky.") },
            snackbarHostState = remember { SnackbarHostState() },
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
            onSuggestionClick = {},
        )
    }
}

@PreviewTest
@Preview(name = "typeahead-suggestions-light", showBackground = true)
@Preview(name = "typeahead-suggestions-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComposerScreenTypeaheadSuggestionsScreenshot() {
    // Renders the composer mid-mention-authoring with a Suggestions
    // dropdown visible. Avatar URLs are null so each row falls back
    // to NubecitaAsyncImage's placeholder ColorPainter — same
    // deterministic render the design-system fixture uses.
    NubecitaTheme(dynamicColor = false) {
        ComposerScreenContent(
            state =
                ComposerState(
                    graphemeCount = 6,
                    typeahead =
                        TypeaheadStatus.Suggestions(
                            query = "al",
                            results =
                                persistentListOf(
                                    ActorTypeaheadUi(
                                        did = "did:plc:alice",
                                        handle = "alice.bsky.social",
                                        displayName = "Alice",
                                        avatarUrl = null,
                                    ),
                                    ActorTypeaheadUi(
                                        did = "did:plc:alex",
                                        handle = "alex.bsky.social",
                                        displayName = "Alex",
                                        avatarUrl = null,
                                    ),
                                    ActorTypeaheadUi(
                                        did = "did:plc:alvin",
                                        handle = "alvin.bsky.social",
                                        displayName = "Alvin",
                                        avatarUrl = null,
                                    ),
                                ),
                        ),
                ),
            textFieldState = remember { TextFieldState(initialText = "hi @al") },
            snackbarHostState = remember { SnackbarHostState() },
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
            onSuggestionClick = {},
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
                    graphemeCount = 29,
                    attachments =
                        persistentListOf(
                            ComposerAttachment(uri = Uri.parse("content://fixture/0"), mimeType = "image/jpeg"),
                            ComposerAttachment(uri = Uri.parse("content://fixture/1"), mimeType = "image/jpeg"),
                            ComposerAttachment(uri = Uri.parse("content://fixture/2"), mimeType = "image/jpeg"),
                        ),
                ),
            textFieldState = remember { TextFieldState(initialText = "Three placeholder attachments") },
            snackbarHostState = remember { SnackbarHostState() },
            onSubmit = {},
            onCloseClick = {},
            onAddImageClick = {},
            onRemoveAttachment = {},
            onSuggestionClick = {},
        )
    }
}
