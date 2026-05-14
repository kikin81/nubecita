package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET

private val SAMPLE_WITH_AVATAR =
    ConvoListItemUi(
        convoId = "c1",
        otherUserDid = "did:plc:alice",
        otherUserHandle = "alice.bsky.social",
        displayName = "Alice Liddell",
        avatarUrl = "https://cdn.example.com/avatars/alice.jpg",
        avatarHue = 217,
        lastMessageSnippet = "I would love a copy if you have one to spare.",
        lastMessageFromViewer = false,
        lastMessageIsAttachment = false,
        timestampRelative = "10m",
    )

private val SAMPLE_FALLBACK_LETTER =
    SAMPLE_WITH_AVATAR.copy(
        otherUserHandle = "bob.bsky.social",
        displayName = "Bob",
        avatarUrl = null,
        avatarHue = 42,
        lastMessageSnippet = "ok",
        timestampRelative = "Yesterday",
    )

private val SAMPLE_YOU_PREFIX =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = "On my way",
        lastMessageFromViewer = true,
        timestampRelative = "3h",
    )

private val SAMPLE_DELETED =
    SAMPLE_WITH_AVATAR.copy(
        lastMessageSnippet = DELETED_MESSAGE_SNIPPET,
        lastMessageFromViewer = false,
        timestampRelative = "Mon",
    )

private val SAMPLE_LONG_SNIPPET =
    SAMPLE_WITH_AVATAR.copy(
        displayName = "Dr. Reginald Wellington-Smythe III",
        lastMessageSnippet =
            "This is a deliberately long message snippet that should wrap to two lines and then ellipsize because the third line of body content would push the row well past 64dp and break the visual rhythm of the list.",
        timestampRelative = "Apr 25",
    )

@PreviewTest
@Preview(name = "convo-with-avatar-light", showBackground = true)
@Preview(name = "convo-with-avatar-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemWithAvatarScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_WITH_AVATAR, index = 0, count = 1, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-fallback-letter-light", showBackground = true)
@Preview(name = "convo-fallback-letter-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemFallbackLetterScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_FALLBACK_LETTER, index = 0, count = 1, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-you-prefix-light", showBackground = true)
@Preview(name = "convo-you-prefix-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemYouPrefixScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_YOU_PREFIX, index = 0, count = 1, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-deleted-light", showBackground = true)
@Preview(name = "convo-deleted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemDeletedScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_DELETED, index = 0, count = 1, onTap = {}) }
    }
}

@PreviewTest
@Preview(name = "convo-long-snippet-light", showBackground = true)
@Preview(name = "convo-long-snippet-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConvoListItemLongSnippetScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface { ConvoListItem(item = SAMPLE_LONG_SNIPPET, index = 0, count = 1, onTap = {}) }
    }
}
