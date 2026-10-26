package net.kikin.nubecita.wear.dms

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/** One message as the watch shows it. */
@Immutable
data class WatchMessageUi(
    val id: String,
    val fromViewer: Boolean,
    /** Set for other people's messages in a group chat. */
    val senderName: String?,
    val text: String,
    val sentAtMs: Long,
    val isDeleted: Boolean,
    val hasAttachment: Boolean,
    val reactions: ImmutableList<WatchReactionUi> = persistentListOf(),
    /** The viewer's reactions on this message that a snapshot doesn't show yet. */
    val pendingReactions: ImmutableList<WatchPendingDmUi> = persistentListOf(),
)

/** One emoji's reactions on a message. */
@Immutable
data class WatchReactionUi(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

/** Where a reply or reaction sent from the watch stands. */
enum class WatchSendStatus { SENDING, SENT, FAILED }

/** A quick reply or reaction sent from the watch, shown until a snapshot carries it. */
@Immutable
data class WatchPendingDmUi(
    val actionId: String,
    /** The reply's text, or the reaction's emoji. */
    val text: String,
    val status: WatchSendStatus,
)

/** A conversation row: its newest message is the preview. */
@Immutable
data class WatchConvoUi(
    val convoId: String,
    val title: String,
    val isGroup: Boolean,
    val unreadCount: Int,
    val muted: Boolean,
    val lastMessageAtMs: Long?,
    val lastMessage: WatchMessageUi?,
)

/** An open conversation. Messages are oldest first. */
@Immutable
data class WatchThreadUi(
    val convoId: String,
    val title: String,
    val isGroup: Boolean,
    val messages: ImmutableList<WatchMessageUi>,
    /** Quick replies sent from the watch that the snapshot doesn't show yet, oldest first. */
    val pendingReplies: ImmutableList<WatchPendingDmUi> = persistentListOf(),
)

/** Exactly one applies. */
sealed interface WatchDmStatus {
    /** The account isn't known yet or the store hasn't answered. */
    data object Loading : WatchDmStatus

    /** The phone hasn't synced DMs yet (message checking off, or no poll since pairing). */
    data object NotSynced : WatchDmStatus

    data object Ready : WatchDmStatus
}

data class WatchDmState(
    val status: WatchDmStatus = WatchDmStatus.Loading,
    /** Unread messages across the synced conversations (muted ones excluded by the phone). */
    val unreadCount: Int = 0,
    val convos: ImmutableList<WatchConvoUi> = persistentListOf(),
    /** The conversation [WatchDmEvent.ThreadOpened] chose; null until one is opened. */
    val thread: WatchThreadUi? = null,
    /** The paired phone runs quick replies and reactions. */
    val actionsAvailable: Boolean = false,
) : UiState

sealed interface WatchDmEvent : UiEvent {
    /** The signed-in account (from the gate). Null clears the DMs. */
    data class AccountChanged(
        val accountDid: String?,
    ) : WatchDmEvent

    data class ThreadOpened(
        val convoId: String,
    ) : WatchDmEvent

    data class ReplySent(
        val convoId: String,
        val text: String,
    ) : WatchDmEvent

    data class ReactionSent(
        val convoId: String,
        val messageId: String,
        val emoji: String,
    ) : WatchDmEvent

    /** Send a failed reply or reaction again. */
    data class RetryTapped(
        val actionId: String,
    ) : WatchDmEvent
}

/** No one-shot effects: navigation is driven by the screens. */
sealed interface WatchDmEffect : UiEffect
