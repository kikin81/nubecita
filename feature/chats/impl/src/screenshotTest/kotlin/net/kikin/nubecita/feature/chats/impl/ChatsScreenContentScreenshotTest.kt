package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
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
    isRequest: Boolean = false,
): ConvoRowUi =
    ConvoRowUi.Direct(
        convoId = convoId,
        otherUserDid = "did:plc:$convoId",
        otherUserHandle = "$convoId.bsky.social",
        displayName = displayName,
        avatarUrl = null,
        lastMessageSnippet = snippet,
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        sentAt = sentAt,
        isRequest = isRequest,
    )

private fun sampleGroupItem(
    convoId: String,
    name: String,
    memberCount: Int,
    isRequest: Boolean = false,
): ConvoRowUi =
    ConvoRowUi.Group(
        convoId = convoId,
        name = name,
        members =
            (1..memberCount)
                .map {
                    AuthorUi(
                        did = "did:plc:$convoId$it",
                        handle = "member$it.bsky.social",
                        displayName = "Member $it",
                        avatarUrl = null,
                    )
                }.toPersistentList(),
        lastMessageSnippet = "So it \u201cshould\u201d background poll for new posts",
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        sentAt = FIXTURE_NOW - 3.hours,
        isRequest = isRequest,
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
        // Exercises the badge on the Requests pill while the Chats segment is active.
        requestCount = 3,
    )

private val REQUESTS_LOADED_STATE =
    ChatsScreenViewState(
        status =
            ChatsLoadStatus.Loaded(
                items =
                    persistentListOf(
                        // isRequest = true is what puts Accept / Decline on the row.
                        sampleItem("dave", "Dave", "hey, mind if I message you?", FIXTURE_NOW - 5.minutes, isRequest = true),
                        sampleItem("erin", "Erin", "saw your post about feeds", FIXTURE_NOW - 3.hours, isRequest = true),
                        // A GROUP request, and specifically one big enough to
                        // overflow the old facepile. Its absence is why the clipped
                        // Accept button in nubecita-mpgs shipped green: every
                        // request fixture here was a direct convo, whose leading
                        // avatar is a single circle.
                        sampleGroupItem("skyline", "Skyline Beta Users", memberCount = 6, isRequest = true),
                    ),
            ),
        activeSegment = ChatsSegment.Requests,
        requestCount = 2,
    )

private val REQUESTS_EMPTY_STATE =
    ChatsScreenViewState(
        status = ChatsLoadStatus.Loaded(items = persistentListOf()),
        activeSegment = ChatsSegment.Requests,
        requestCount = 0,
    )

private val REQUESTS_ERROR_STATE =
    ChatsScreenViewState(
        status = ChatsLoadStatus.InitialError(ChatsError.Network),
        activeSegment = ChatsSegment.Requests,
        requestCount = 0,
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

// Multi-select on the Chats segment: the contextual bar replaces the toolbar
// (count + Mute + Leave); the segment row + FAB are hidden; alice & carol read
// as selected (secondaryContainer). >1 selected, so no single-only overflow.
private val SELECTION_MULTI_STATE =
    LOADED_STATE.copy(selection = persistentSetOf("alice", "carol"))

// Single-select on the Chats segment: same bar but with the ⋮ overflow visible
// (Profile / Report / Block become available at exactly one selection).
private val SELECTION_SINGLE_STATE =
    LOADED_STATE.copy(selection = persistentSetOf("bob"))

// Single-select on the Requests segment: Accept replaces Mute as the inline
// bulk action; Leave declines the request.
private val REQUESTS_SELECTION_STATE =
    REQUESTS_LOADED_STATE.copy(selection = persistentSetOf("dave"))

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
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = LOADED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-loaded-selected-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loaded-selected-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadedSelectedScreenshot() {
    // Tablet list-detail layout: the open thread ("bob") highlights as the
    // selected row (secondaryContainer) against the surfaceContainer rows.
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(
                state = LOADED_STATE,
                snackbarHostState = remember { SnackbarHostState() },
                onEvent = {},
                onNewChat = {},
                onNewGroup = {},
                selectedConvoId = "bob",
            )
        }
    }
}

@PreviewTest
@Preview(name = "chats-selection-multi-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-selection-multi-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenSelectionMultiScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = SELECTION_MULTI_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-selection-single-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-selection-single-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenSelectionSingleScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = SELECTION_SINGLE_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-requests-selection-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-requests-selection-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenRequestsSelectionScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = REQUESTS_SELECTION_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-loaded-refreshing-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loaded-refreshing-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadedRefreshingScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = LOADED_REFRESHING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatsScreenContent(state = EMPTY_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
    }
}

@PreviewTest
@Preview(name = "chats-requests-loaded-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-requests-loaded-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenRequestsLoadedScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = REQUESTS_LOADED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}

@PreviewTest
@Preview(name = "chats-requests-empty-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-requests-empty-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenRequestsEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatsScreenContent(state = REQUESTS_EMPTY_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
    }
}

@PreviewTest
@Preview(name = "chats-requests-error-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-requests-error-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenRequestsErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatsScreenContent(state = REQUESTS_ERROR_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
    }
}

@PreviewTest
@Preview(name = "chats-loading-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-loading-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatsScreenContent(state = LOADING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
    }
}

@PreviewTest
@Preview(name = "chats-network-error-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-network-error-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNetworkErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatsScreenContent(state = NETWORK_ERROR_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
    }
}

@PreviewTest
@Preview(name = "chats-not-enrolled-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-not-enrolled-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsScreenNotEnrolledScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatsScreenContent(state = NOT_ENROLLED_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
    }
}

// One row mid-accept: its actions disable and Accept shows the in-button spinner
// while the other row stays interactive, which is the point of tracking in-flight
// state per convo id rather than as a single screen-wide flag.
private val REQUESTS_ACCEPTING_STATE =
    REQUESTS_LOADED_STATE.copy(acceptInFlight = persistentSetOf("dave"))

@PreviewTest
@Preview(name = "chats-requests-accepting-light", showBackground = true, heightDp = 600)
@Preview(name = "chats-requests-accepting-dark", showBackground = true, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatsRequestsAcceptingScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaCanvasPreviewTheme {
            ChatsScreenContent(state = REQUESTS_ACCEPTING_STATE, snackbarHostState = remember { SnackbarHostState() }, onEvent = {}, onNewChat = {}, onNewGroup = {})
        }
    }
}
