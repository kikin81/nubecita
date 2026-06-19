package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// `now` is pinned to a fixed Instant so the row's timestamp label —
// produced live by `rememberChatRelativeTimeText` — renders the same
// string on every screenshot run. May 13 2026 is a Wednesday, picked so
// the `2 days ago` sample renders "Mon" (matches the legacy bake before
// nubecita-nn3.3). The reference fixture clock pattern mirrors the
// FeedScreen / PostDetail screenshot tests.
private val FIXTURE_NOW = Instant.parse("2026-05-13T12:00:00Z")

private object FixtureClock : Clock {
    override fun now(): Instant = FIXTURE_NOW
}

private val SAMPLE_WITH_AVATAR =
    ConvoListItemUi(
        convoId = "c1",
        otherUserDid = "did:plc:alice",
        otherUserHandle = "alice.bsky.social",
        displayName = "Alice Liddell",
        avatarUrl = "https://cdn.example.com/avatars/alice.jpg",
        lastMessageSnippet = "I would love a copy if you have one to spare.",
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        sentAt = FIXTURE_NOW - 10.minutes, // renders "10m"
    )

private val SAMPLE_FALLBACK_LETTER =
    SAMPLE_WITH_AVATAR.copy(
        otherUserHandle = "bob.bsky.social",
        displayName = "Bob",
        avatarUrl = null,
        lastMessageSnippet = "ok",
        sentAt = FIXTURE_NOW - 28.hours, // renders "Yesterday"
    )

private val SAMPLE_YOU_PREFIX =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = "On my way",
        lastMessageFromViewer = true,
        sentAt = FIXTURE_NOW - 3.hours, // renders "3h"
    )

private val SAMPLE_DELETED =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = DELETED_MESSAGE_SNIPPET,
        lastMessageFromViewer = false,
        sentAt = FIXTURE_NOW - 2.days, // 2026-05-11 = Monday → renders "Mon"
    )

private val SAMPLE_LONG_SNIPPET =
    SAMPLE_WITH_AVATAR.copy(
        displayName = "Dr. Reginald Wellington-Smythe III",
        lastMessageSnippet =
            "This is a deliberately long message snippet that should wrap to two lines and then ellipsize because the third line of body content would push the row well past 64dp and break the visual rhythm of the list.",
        sentAt = Instant.parse("2026-04-25T12:00:00Z"), // > 7 days back, same year → "Apr 25"
    )

private val SAMPLE_UNREAD =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = "did you see this?",
        sentAt = FIXTURE_NOW - 5.minutes, // renders "5m"
        unreadCount = 3, // → bold name + "3" badge
    )

@PreviewTest
@Preview(name = "convo-unread-light", showBackground = true)
@Preview(name = "convo-unread-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemUnreadScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface { ConvoListItem(item = SAMPLE_UNREAD, index = 0, count = 1, onClick = {}, onLongClick = {}) }
        }
    }
}

@PreviewTest
@Preview(name = "convo-with-avatar-light", showBackground = true)
@Preview(name = "convo-with-avatar-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemWithAvatarScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface { ConvoListItem(item = SAMPLE_WITH_AVATAR, index = 0, count = 1, onClick = {}, onLongClick = {}) }
        }
    }
}

@PreviewTest
@Preview(name = "convo-fallback-letter-light", showBackground = true)
@Preview(name = "convo-fallback-letter-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemFallbackLetterScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface { ConvoListItem(item = SAMPLE_FALLBACK_LETTER, index = 0, count = 1, onClick = {}, onLongClick = {}) }
        }
    }
}

@PreviewTest
@Preview(name = "convo-you-prefix-light", showBackground = true)
@Preview(name = "convo-you-prefix-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemYouPrefixScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface { ConvoListItem(item = SAMPLE_YOU_PREFIX, index = 0, count = 1, onClick = {}, onLongClick = {}) }
        }
    }
}

@PreviewTest
@Preview(name = "convo-deleted-light", showBackground = true)
@Preview(name = "convo-deleted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemDeletedScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface { ConvoListItem(item = SAMPLE_DELETED, index = 0, count = 1, onClick = {}, onLongClick = {}) }
        }
    }
}

@PreviewTest
@Preview(name = "convo-long-snippet-light", showBackground = true)
@Preview(name = "convo-long-snippet-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemLongSnippetScreenshot() {
    CompositionLocalProvider(LocalClock provides FixtureClock) {
        NubecitaTheme(dynamicColor = false) {
            Surface { ConvoListItem(item = SAMPLE_LONG_SNIPPET, index = 0, count = 1, onClick = {}, onLongClick = {}) }
        }
    }
}
