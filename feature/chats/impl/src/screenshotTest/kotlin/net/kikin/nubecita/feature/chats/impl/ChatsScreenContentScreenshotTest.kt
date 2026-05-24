package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.designsystem.preview.NubecitaScreenPreviewTheme
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// `now` pinned so rememberChatRelativeTimeText renders the same labels
// on every screenshot run. Aligned with ConvoListItemScreenshotTest.
private val FIXTURE_NOW = Instant.parse("2026-05-13T12:00:00Z")

private object FixtureClock : Clock {
    override fun now(): Instant = FIXTURE_NOW
}

private fun sampleItem(
    convoId: String,
    displayName: String?,
    snippet: String,
    sentAt: Instant?,
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
        sentAt = sentAt,
    )

private val LOADED_STATE =
    ChatsScreenViewState(
        status =
            ChatsLoadStatus.Loaded(
                items =
                    persistentListOf(
                        sampleItem("alice", "Alice Liddell", "I would love a copy.", FIXTURE_NOW - 10.minutes),
                        sampleItem("bob", "Bob", "ok", FIXTURE_NOW - 28.hours),
                        sampleItem("carol", null, "see you soon", FIXTURE_NOW - 2.days),
                    ),
            ),
    )

private val LOADED_REFRESHING_STATE =
    ChatsScreenViewState(
        status =
            ChatsLoadStatus.Loaded(
                items =
                    persistentListOf(
                        sampleItem("alice", "Alice Liddell", "I would love a copy.", FIXTURE_NOW - 10.minutes),
                        sampleItem("bob", "Bob", "ok", FIXTURE_NOW - 28.hours),
                        sampleItem("carol", null, "see you soon", FIXTURE_NOW - 2.days),
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
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaScreenPreviewTheme {
            ChatsScreenContent(state = LOADED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-loaded-refreshing-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loaded-refreshing-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadedRefreshingScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaScreenPreviewTheme {
            ChatsScreenContent(state = LOADED_REFRESHING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenEmptyScreenshot() {
    NubecitaScreenPreviewTheme {
        ChatsScreenContent(state = EMPTY_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-loading-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loading-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadingScreenshot() {
    NubecitaScreenPreviewTheme {
        ChatsScreenContent(state = LOADING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-network-error-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-network-error-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNetworkErrorScreenshot() {
    NubecitaScreenPreviewTheme {
        ChatsScreenContent(state = NETWORK_ERROR_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}

@PreviewTest
@Preview(name = "chats-not-enrolled-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-not-enrolled-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNotEnrolledScreenshot() {
    NubecitaScreenPreviewTheme {
        ChatsScreenContent(state = NOT_ENROLLED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {})
    }
}
