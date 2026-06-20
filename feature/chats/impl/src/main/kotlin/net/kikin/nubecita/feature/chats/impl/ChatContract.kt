package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import kotlin.time.Instant

/**
 * Identity of the open conversation, branched by kind. Direct = the single other
 * member (today's behavior); Group = the group's name + members for the AvatarGroup
 * facepile. Consumed by the Chat thread TopAppBar (wired in a later task).
 */
@Immutable
sealed interface ChatHeader {
    @Immutable
    data class Direct(
        val did: String,
        val handle: String,
        val displayName: String?,
        val avatarUrl: String?,
    ) : ChatHeader

    @Immutable
    data class Group(
        val name: String,
        val members: ImmutableList<AuthorUi>,
    ) : ChatHeader
}

/**
 * MVI state for the Chat thread screen. Mirrors `ChatsScreenViewState` shape: one
 * sealed [ChatLoadStatus] sum carries the lifecycle; `isRefreshing` is the only
 * flag that may coexist with `Loaded` and lives inside that variant.
 *
 * The kind-aware [header] (Direct vs Group) is set after the convo loads — it is
 * `null` during the initial Loading composition — and drives the TopAppBar.
 * [canPost] gates the composer (a read-only convo disables send); it defaults to
 * `true` so the composer is enabled the moment a postable convo resolves.
 */
data class ChatScreenViewState(
    val header: ChatHeader? = null,
    val canPost: Boolean = true,
    val status: ChatLoadStatus = ChatLoadStatus.Loading,
    val isSendEnabled: Boolean = false,
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

    /**
     * Peer cannot accept a DM from the signed-in viewer. Covers two
     * `getConvoForMembers` wire codes that produce the same UX outcome:
     *
     * - `MessagesDisabled` — recipient turned off incoming DMs entirely
     *   (`associated.chat.allowIncoming = "none"`).
     * - `NotFollowedBySender` — recipient accepts only follows-only DMs
     *   and the chat appview's view of the follow graph does not show
     *   them following the viewer at request time. The Profile screen's
     *   `canMessage` gate hides the button when `viewer.followedBy` is
     *   non-null, so this branch typically only fires on appview lag
     *   against the follow graph.
     *
     * Retry would never succeed for either; the error renders without
     * a Retry affordance.
     */
    data object MessagesDisabled : ChatError

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
 *
 * `sender` carries the resolved sender profile for GROUP incoming rows — the
 * wire `MessageView` only carries the sender DID, so the profile is JOINED from
 * the group's loaded members and threaded through `toThreadItems`. It is `null`
 * in 1:1 (direct) threads and for the viewer's own (outgoing) messages, both of
 * which render bare (no avatar / name).
 */
@Immutable
sealed interface ThreadItem {
    val key: String

    data class Message(
        val message: MessageUi,
        val runIndex: Int,
        val runCount: Int,
        val showAvatar: Boolean,
        val sender: AuthorUi? = null,
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

/**
 * One message in a chat thread.
 *
 * [embed] carries the rendered record-embed payload when the wire
 * `MessageView.embed` resolved to `app.bsky.embed.record#view`. The
 * chat lexicon (`chat.bsky.convo.defs#messageView.embed`) only admits
 * the record-embed variant — external links, images, and video are
 * not part of the chat wire format and surface either as plain `text`
 * (external URLs, facets-pending) or are not transmittable at all.
 * That constraint is why `embed` is typed as
 * [EmbedUi.RecordOrUnavailable] (Record + RecordUnavailable) rather
 * than the broader [EmbedUi].
 */
@Immutable
data class MessageUi(
    val id: String,
    val senderDid: String,
    val isOutgoing: Boolean,
    val text: String,
    val isDeleted: Boolean,
    val sentAt: Instant,
    val embed: EmbedUi.RecordOrUnavailable? = null,
    val sendStatus: MessageSendStatus = MessageSendStatus.Sent,
)

/**
 * Per-message send lifecycle for outgoing (composed) messages. Server-fetched
 * messages from `getMessages` are always [Sent] (the default), so the read path
 * and all existing fixtures are unaffected. The composer's optimistic echo
 * starts [Sending]; a confirmed server echo reconciles to [Sent]; a failed send
 * flips to [Failed] (inline retry UX is a follow-up — child D).
 */
enum class MessageSendStatus {
    Sending,
    Sent,
    Failed,
}

sealed interface ChatEvent : UiEvent {
    data object Refresh : ChatEvent

    data object RetryClicked : ChatEvent

    data object BackPressed : ChatEvent

    /** User tapped send (or pressed the IME Send action) with non-blank composer text. */
    data object Send : ChatEvent

    /**
     * User tapped the inline retry affordance on a `Failed` outgoing row.
     * [tempId] is the optimistic row's client temp id (`local:<n>`); the VM
     * flips that row back to `Sending` and re-issues `sendMessage` with its
     * text. A no-op if the row no longer exists or isn't `Failed`.
     */
    data class RetrySend(
        val tempId: String,
    ) : ChatEvent

    /**
     * User tapped the quoted-post embed under a message bubble. The VM
     * translates this to [ChatEffect.NavigateToPost] so the screen can
     * push `PostDetailRoute(postUri = quotedPostUri)` onto the active
     * tab's back stack.
     */
    data class QuotedPostTapped(
        val quotedPostUri: String,
    ) : ChatEvent
}

sealed interface ChatEffect : UiEffect {
    /** Push the post-detail screen for the tapped quoted-post URI. */
    data class NavigateToPost(
        val postUri: String,
    ) : ChatEffect

    /**
     * A send (or retry) failed. Surfaced once as a transient snackbar by the
     * screen's effect collector; the sticky failure also lives on the row's
     * `Failed` [MessageSendStatus] with an inline retry affordance. The
     * [error] reuses the thread-load [ChatError] taxonomy via `toChatError`.
     */
    data class ShowSendError(
        val error: ChatError,
    ) : ChatEffect
}
