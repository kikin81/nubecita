package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import kotlin.time.Instant

/**
 * MVI state for the Chat thread screen. Mirrors `ChatsScreenViewState` shape: one
 * sealed [ChatLoadStatus] sum carries the lifecycle; `isRefreshing` is the only
 * flag that may coexist with `Loaded` and lives inside that variant.
 *
 * The other-user's profile bits (handle/displayName/avatar) populate after the
 * `resolveConvo` call returns; the TopAppBar reads them. Defaults are empty
 * strings / nulls so the initial Loading composition has stable values.
 */
data class ChatScreenViewState(
    val otherUserHandle: String = "",
    val otherUserDisplayName: String? = null,
    val otherUserAvatarUrl: String? = null,
    val otherUserAvatarHue: Int = 0,
    val status: ChatLoadStatus = ChatLoadStatus.Loading,
) : UiState

sealed interface ChatLoadStatus {
    data object Loading : ChatLoadStatus

    data class Loaded(
        val items: ImmutableList<ThreadItem> = persistentListOf(),
        val isRefreshing: Boolean = false,
    ) : ChatLoadStatus

    data class InitialError(
        val error: ChatError,
    ) : ChatLoadStatus
}

sealed interface ChatError {
    data object Network : ChatError

    data object NotEnrolled : ChatError

    /** `resolveConvo` couldn't find or open a convo for the peer DID. */
    data object ConvoNotFound : ChatError

    data class Unknown(
        val cause: String?,
    ) : ChatError
}

/**
 * Flat sealed list of items rendered by the thread `LazyColumn`. The mapper
 * emits this newest-first; `LazyColumn(reverseLayout = true)` then renders
 * bottom-to-top so the freshest message lands at the screen bottom.
 *
 * `runIndex` is OLDEST-first within the run: the oldest message of a run gets
 * `runIndex = 0` (top of run on screen with reverseLayout); the newest gets
 * `runCount - 1` (bottom of run on screen).
 */
@Immutable
sealed interface ThreadItem {
    val key: String

    data class Message(
        val message: MessageUi,
        val runIndex: Int,
        val runCount: Int,
        val showAvatar: Boolean,
    ) : ThreadItem {
        override val key: String get() = "msg-${message.id}"
    }

    data class DaySeparator(
        val epochDay: Long,
        val label: String,
    ) : ThreadItem {
        override val key: String get() = "sep-$epochDay"
    }
}

@Immutable
data class MessageUi(
    val id: String,
    val senderDid: String,
    val isOutgoing: Boolean,
    val text: String,
    val isDeleted: Boolean,
    val sentAt: Instant,
)

sealed interface ChatEvent : UiEvent {
    data object Refresh : ChatEvent

    data object RetryClicked : ChatEvent

    data object BackPressed : ChatEvent
}

sealed interface ChatEffect : UiEffect
