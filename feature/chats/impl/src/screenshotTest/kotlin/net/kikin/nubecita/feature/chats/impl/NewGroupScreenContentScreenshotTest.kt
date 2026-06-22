package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [NewGroupScreenContent] — the new-group creation
 * screen. The baselines exercise the empty/recent body, the named + selected +
 * results body, the at-capacity hint, the proactive name counter, the submitting
 * (disabled inputs + spinner) state, and the wide-screen 600.dp body constraint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "new-group-empty-light", showBackground = true, heightDp = 720)
@Preview(name = "new-group-empty-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewGroupEmpty() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    status =
                        NewGroupStatus.Recent(
                            persistentListOf(
                                ActorUi("did:a", "alice.bsky.social", "Alice", null),
                            ),
                        ),
                ),
            nameFieldState = TextFieldState(),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "new-group-named-selection-light", showBackground = true, heightDp = 720)
@Preview(name = "new-group-named-selection-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewGroupNamedWithSelection() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected =
                        persistentListOf(
                            RecipientUi("did:a", "alice.bsky.social", "Alice", null),
                            RecipientUi("did:b", "bob.bsky.social", null, null),
                        ),
                    nameGraphemeCount = 13,
                    isNameValid = true,
                    status =
                        NewGroupStatus.Results(
                            persistentListOf(
                                ActorUi("did:c", "carol.bsky.social", "Carol", null),
                            ),
                        ),
                ),
            nameFieldState = TextFieldState(initialText = "Trip planning"),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "new-group-at-capacity-light", showBackground = true, heightDp = 720)
@Preview(name = "new-group-at-capacity-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewGroupAtCapacity() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected =
                        persistentListOf(
                            RecipientUi("did:a", "alice.bsky.social", "Alice", null),
                        ),
                    atCapacity = true,
                    status =
                        NewGroupStatus.Results(
                            persistentListOf(
                                ActorUi("did:c", "carol.bsky.social", "Carol", null),
                            ),
                        ),
                ),
            nameFieldState = TextFieldState(),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "new-group-name-counter-light", showBackground = true, heightDp = 720)
@Preview(name = "new-group-name-counter-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewGroupNameCounter() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    nameGraphemeCount = 120,
                    isNameValid = true,
                    status = NewGroupStatus.Recent(persistentListOf()),
                ),
            nameFieldState = TextFieldState(initialText = "a".repeat(120)),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "new-group-submitting-light", showBackground = true, heightDp = 720)
@Preview(name = "new-group-submitting-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewGroupSubmitting() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected =
                        persistentListOf(
                            RecipientUi("did:a", "alice.bsky.social", "Alice", null),
                        ),
                    nameGraphemeCount = 13,
                    isNameValid = true,
                    isSubmitting = true,
                    status = NewGroupStatus.Results(persistentListOf()),
                ),
            nameFieldState = TextFieldState(initialText = "Trip planning"),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "new-group-wide-light", showBackground = true, widthDp = 840, heightDp = 720)
@Preview(name = "new-group-wide-dark", showBackground = true, widthDp = 840, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewGroupWide() {
    NubecitaCanvasPreviewTheme {
        NewGroupScreenContent(
            state =
                NewGroupViewState(
                    selected =
                        persistentListOf(
                            RecipientUi("did:a", "alice.bsky.social", "Alice", null),
                            RecipientUi("did:b", "bob.bsky.social", null, null),
                        ),
                    nameGraphemeCount = 13,
                    isNameValid = true,
                    status =
                        NewGroupStatus.Results(
                            persistentListOf(
                                ActorUi("did:c", "carol.bsky.social", "Carol", null),
                            ),
                        ),
                ),
            nameFieldState = TextFieldState(initialText = "Trip planning"),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onClose = {},
        )
    }
}
