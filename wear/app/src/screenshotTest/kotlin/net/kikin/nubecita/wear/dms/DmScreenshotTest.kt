package net.kikin.nubecita.wear.dms

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

// Rendered without AppScaffold (its TimeText shows the wall clock) and with a
// fixed "now", so every baseline is deterministic.

private const val NOW = 1_800_000_000_000L

private fun msg(
    id: String,
    text: String,
    minutesAgo: Int,
    mine: Boolean = false,
    sender: String? = null,
    deleted: Boolean = false,
) = WatchMessageUi(id = id, fromViewer = mine, senderName = sender, text = if (deleted) "" else text, sentAtMs = NOW - minutesAgo * 60_000L, isDeleted = deleted, hasAttachment = false)

private val DIRECT =
    persistentListOf(
        msg("1", "Are we still on for the hike Saturday?", 12),
        msg("2", "Yes! 8am at the trailhead.", 9, mine = true),
        msg("3", "Perfect, I'll bring coffee.", 3),
    )

private val GROUP =
    persistentListOf(
        msg("1", "Who's driving?", 50, sender = "Ben"),
        msg("2", "I can, the car fits four.", 44, sender = "Cleo"),
        msg("3", "", 42, sender = "Ben", deleted = true),
        msg("4", "I'll bring snacks.", 40, mine = true),
    )

private val READY =
    WatchDmState(
        status = WatchDmStatus.Ready,
        unreadCount = 3,
        convos =
            persistentListOf(
                WatchConvoUi("dm-ana", "Ana Ruiz", false, unreadCount = 2, muted = false, lastMessageAtMs = NOW - 3 * 60_000L, lastMessage = DIRECT.last()),
                WatchConvoUi("dm-trip", "Weekend Trip", true, unreadCount = 1, muted = false, lastMessageAtMs = NOW - 40 * 60_000L, lastMessage = GROUP.last()),
                WatchConvoUi("dm-cleo", "Cleo Marsh", false, unreadCount = 0, muted = false, lastMessageAtMs = NOW - 180 * 60_000L, lastMessage = msg("c", "Loved the lake photos!", 180)),
            ),
    )

@PreviewTest
@WearPreviewDevices
@Composable
private fun ConvoListPreview() {
    NubecitaWearTheme { ConvoListScreen(state = READY, nowMs = NOW, onOpen = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun ConvoListFontScalesPreview() {
    NubecitaWearTheme { ConvoListScreen(state = READY, nowMs = NOW, onOpen = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun ConvoListNotSyncedPreview() {
    NubecitaWearTheme { ConvoListScreen(state = WatchDmState(status = WatchDmStatus.NotSynced), nowMs = NOW, onOpen = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun ThreadDirectPreview() {
    NubecitaWearTheme { ThreadScreen(thread = WatchThreadUi("dm-ana", "Ana Ruiz", false, DIRECT), nowMs = NOW) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun ThreadGroupPreview() {
    NubecitaWearTheme { ThreadScreen(thread = WatchThreadUi("dm-trip", "Weekend Trip", true, GROUP), nowMs = NOW) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun ThreadFontScalesPreview() {
    NubecitaWearTheme { ThreadScreen(thread = WatchThreadUi("dm-ana", "Ana Ruiz", false, DIRECT), nowMs = NOW) }
}

private val WITH_ACTIVITY =
    WatchThreadUi(
        convoId = "dm-ana",
        title = "Ana Ruiz",
        isGroup = false,
        messages =
            persistentListOf(
                DIRECT[0],
                DIRECT[1],
                DIRECT[2].copy(
                    reactions = persistentListOf(WatchReactionUi("❤️", 2, mine = false)),
                    pendingReactions = persistentListOf(WatchPendingDmUi("x1", "👍", WatchSendStatus.FAILED)),
                ),
            ),
        pendingReplies = persistentListOf(WatchPendingDmUi("r1", "On my way", WatchSendStatus.SENDING)),
    )

@PreviewTest
@WearPreviewDevices
@Composable
private fun ThreadWithRepliesPreview() {
    // A reaction chip, a reaction that didn't send, a reply still sending, and Reply.
    NubecitaWearTheme { ThreadScreen(thread = WITH_ACTIVITY, nowMs = NOW, onReply = {}, onReact = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun QuickReplyPreview() {
    NubecitaWearTheme { QuickReplyScreen(onPick = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun QuickReplyFontScalesPreview() {
    NubecitaWearTheme { QuickReplyScreen(onPick = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun ReactionPreview() {
    NubecitaWearTheme { ReactionScreen(onPick = {}) }
}
