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
) : UiState

sealed interface WatchDmEvent : UiEvent {
    /** The signed-in account (from the gate). Null clears the DMs. */
    data class AccountChanged(
        val accountDid: String?,
    ) : WatchDmEvent

    data class ThreadOpened(
        val convoId: String,
    ) : WatchDmEvent
}

/** No one-shot effects: navigation is driven by the screens. */
sealed interface WatchDmEffect : UiEffect
