package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

private const val VIEWER = "did:plc:viewer"
private const val PEER = "did:plc:alice"

private fun mu(
    id: String,
    isOutgoing: Boolean,
    text: String,
    sentAt: String,
    isDeleted: Boolean = false,
): MessageUi =
    MessageUi(
        id = id,
        senderDid = if (isOutgoing) VIEWER else PEER,
        isOutgoing = isOutgoing,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse(sentAt),
    )

private val LOADED_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        otherUserAvatarHue = 217,
        status =
            ChatLoadStatus.Loaded(
                items =
                    persistentListOf(
                        ThreadItem.DaySeparator(label = "Today"),
                        ThreadItem.Message(
                            message = mu("m4", isOutgoing = false, text = "see you soon", sentAt = "2026-05-14T17:35:00Z"),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = true,
                        ),
                        ThreadItem.Message(
                            message = mu("m3", isOutgoing = true, text = "On my way", sentAt = "2026-05-14T17:30:00Z"),
                            runIndex = 0,
                            runCount = 1,
                            showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message = mu("m2", isOutgoing = false, text = "Quick coffee?", sentAt = "2026-05-14T17:28:00Z"),
                            runIndex = 1,
                            runCount = 2,
                            showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message = mu("m1", isOutgoing = false, text = "Hey", sentAt = "2026-05-14T17:27:00Z"),
                            runIndex = 0,
                            runCount = 2,
                            showAvatar = true,
                        ),
                    ),
            ),
    )

private val EMPTY_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.Loaded(items = persistentListOf()),
    )

private val LOADING_STATE =
    ChatScreenViewState(otherUserHandle = "alice.bsky.social", otherUserDisplayName = "Alice")

private val NETWORK_ERROR_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.InitialError(ChatError.Network),
    )

private val NOT_ENROLLED_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.InitialError(ChatError.NotEnrolled),
    )

@PreviewTest
@Preview(name = "chat-loaded-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-loaded-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenLoadedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = LOADED_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-empty-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-empty-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenEmptyScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = EMPTY_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-loading-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-loading-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenLoadingScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = LOADING_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-network-error-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-network-error-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenNetworkErrorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = NETWORK_ERROR_STATE, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chat-not-enrolled-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-not-enrolled-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatScreenNotEnrolledScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ChatScreenContent(state = NOT_ENROLLED_STATE, onEvent = {})
    }
}
