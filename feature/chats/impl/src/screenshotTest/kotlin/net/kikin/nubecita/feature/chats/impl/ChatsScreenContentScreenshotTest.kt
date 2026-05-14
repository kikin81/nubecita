package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme

private fun sampleItem(
    convoId: String,
    displayName: String?,
    snippet: String,
    ts: String,
): ConvoListItemUi =
    ConvoListItemUi(
        convoId = convoId,
        otherUserDid = "did:plc:$convoId",
        otherUserHandle = "$convoId.bsky.social",
        displayName = displayName,
        avatarUrl = null,
        avatarHue = (convoId.hashCode() and 0x7fffffff) % 360,
        lastMessageSnippet = snippet,
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        timestampRelative = ts,
    )

private val LOADED_STATE =
    ChatsScreenViewState(
        status =
            ChatsLoadStatus.Loaded(
                items =
                    persistentListOf(
                        sampleItem("alice", "Alice Liddell", "I would love a copy.", "10m"),
                        sampleItem("bob", "Bob", "ok", "Yesterday"),
                        sampleItem("carol", null, "see you soon", "Mon"),
                    ),
            ),
    )

private val LOADED_REFRESHING_STATE =
    ChatsScreenViewState(
        status =
            ChatsLoadStatus.Loaded(
                items =
                    persistentListOf(
                        sampleItem("alice", "Alice Liddell", "I would love a copy.", "10m"),
                        sampleItem("bob", "Bob", "ok", "Yesterday"),
                        sampleItem("carol", null, "see you soon", "Mon"),
                    ),
                isRefreshing = true,
            ),
    )

private val EMPTY_STATE = ChatsScreenViewState(status = ChatsLoadStatus.Loaded(items = persistentListOf()))
private val NETWORK_ERROR_STATE = ChatsScreenViewState(status = ChatsLoadStatus.InitialError(ChatsError.Network))
private val NOT_ENROLLED_STATE = ChatsScreenViewState(status = ChatsLoadStatus.InitialError(ChatsError.NotEnrolled))
private val LOADING_STATE = ChatsScreenViewState(status = ChatsLoadStatus.Loading)

@PreviewTest
@Preview(name = "chats-loaded-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loaded-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = LOADED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-loaded-refreshing-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loaded-refreshing-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadedRefreshingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = LOADED_REFRESHING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = EMPTY_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-loading-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loading-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = LOADING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-network-error-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-network-error-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNetworkErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = NETWORK_ERROR_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-not-enrolled-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-not-enrolled-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNotEnrolledScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatsScreenContent(state = NOT_ENROLLED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}
