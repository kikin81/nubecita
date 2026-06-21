package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [AddGroupMembersScreenContent] — the add-group-members
 * recipient picker. The three baselines exercise the picked-chips + results body,
 * the empty-selection recent body, and the at-capacity hint.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@PreviewTest
@Preview(name = "add-members-selection-light", showBackground = true, heightDp = 640)
@Preview(name = "add-members-selection-dark", showBackground = true, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddGroupMembersWithSelection() {
    NubecitaCanvasPreviewTheme {
        AddGroupMembersScreenContent(
            state =
                AddGroupMembersViewState(
                    selected =
                        persistentListOf(
                            RecipientUi("did:a", "alice.bsky.social", "Alice", null),
                            RecipientUi("did:b", "bob.bsky.social", null, null),
                        ),
                    status =
                        AddMembersStatus.Results(
                            persistentListOf(
                                ActorUi("did:c", "carol.bsky.social", "Carol", null),
                                ActorUi("did:d", "dave.bsky.social", null, null),
                            ),
                        ),
                ),
            queryFieldState = remember { TextFieldState() },
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@PreviewTest
@Preview(name = "add-members-recent-light", showBackground = true, heightDp = 640)
@Preview(name = "add-members-recent-dark", showBackground = true, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddGroupMembersRecent() {
    NubecitaCanvasPreviewTheme {
        AddGroupMembersScreenContent(
            state =
                AddGroupMembersViewState(
                    status =
                        AddMembersStatus.Recent(
                            persistentListOf(
                                ActorUi("did:c", "carol.bsky.social", "Carol", null),
                            ),
                        ),
                ),
            queryFieldState = remember { TextFieldState() },
            onEvent = {},
            onClose = {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@PreviewTest
@Preview(name = "add-members-at-capacity-light", showBackground = true, heightDp = 640)
@Preview(name = "add-members-at-capacity-dark", showBackground = true, heightDp = 640, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddGroupMembersAtCapacity() {
    NubecitaCanvasPreviewTheme {
        AddGroupMembersScreenContent(
            state =
                AddGroupMembersViewState(
                    selected =
                        persistentListOf(
                            RecipientUi("did:a", "alice.bsky.social", "Alice", null),
                        ),
                    atCapacity = true,
                    status =
                        AddMembersStatus.Results(
                            persistentListOf(
                                ActorUi("did:c", "carol.bsky.social", "Carol", null),
                            ),
                        ),
                ),
            queryFieldState = remember { TextFieldState() },
            onEvent = {},
            onClose = {},
        )
    }
}
