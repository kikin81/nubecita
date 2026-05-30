package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private val FIXTURE_ACTORS =
    persistentListOf(
        ActorUi(
            did = "did:plc:alice",
            handle = "alice.bsky.social",
            displayName = "Alice Liddell",
            avatarUrl = null,
        ),
        ActorUi(
            did = "did:plc:bob",
            handle = "bob.bsky.social",
            displayName = "Bob",
            avatarUrl = null,
        ),
    )

// A messageable actor next to one the viewer can't DM, to capture the disabled
// "Can't be messaged" row treatment.
private val FIXTURE_ACTORS_MIXED =
    persistentListOf(
        ActorUi(
            did = "did:plc:alice",
            handle = "alice.bsky.social",
            displayName = "Alice Liddell",
            avatarUrl = null,
        ),
        ActorUi(
            did = "did:plc:carol",
            handle = "carol.bsky.social",
            displayName = "Carol",
            avatarUrl = null,
            canMessage = false,
        ),
    )

@PreviewTest
@Preview(name = "new-chat-recent-light", showBackground = true, heightDp = 600)
@Preview(name = "new-chat-recent-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenRecentScreenshot() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Recent(FIXTURE_ACTORS)),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "new-chat-searching-light", showBackground = true, heightDp = 600)
@Preview(name = "new-chat-searching-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenSearchingScreenshot() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Searching),
            queryFieldState = TextFieldState(initialText = "ali"),
            onEvent = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "new-chat-results-light", showBackground = true, heightDp = 600)
@Preview(name = "new-chat-results-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenResultsScreenshot() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Results(FIXTURE_ACTORS)),
            queryFieldState = TextFieldState(initialText = "alice"),
            onEvent = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "new-chat-results-mixed-light", showBackground = true, heightDp = 600)
@Preview(name = "new-chat-results-mixed-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenResultsWithDisabledScreenshot() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Results(FIXTURE_ACTORS_MIXED)),
            queryFieldState = TextFieldState(initialText = "a"),
            onEvent = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "new-chat-no-results-light", showBackground = true, heightDp = 600)
@Preview(name = "new-chat-no-results-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenNoResultsScreenshot() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.NoResults),
            queryFieldState = TextFieldState(initialText = "xyzzy"),
            onEvent = {},
            onBack = {},
        )
    }
}

@PreviewTest
@Preview(name = "new-chat-error-light", showBackground = true, heightDp = 600)
@Preview(name = "new-chat-error-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NewChatScreenErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        NewChatScreenContent(
            state = NewChatState(status = NewChatStatus.Error),
            queryFieldState = TextFieldState(),
            onEvent = {},
            onBack = {},
        )
    }
}
