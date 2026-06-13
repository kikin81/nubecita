package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import kotlin.time.Instant

/**
 * MVI state for the Chats tab home.
 *
 * The screen has a single mutually-exclusive lifecycle (loading /
 * loaded / error) — per `CLAUDE.md`'s MVI conventions that lives in a
 * sealed [ChatsLoadStatus] sum, not in independent boolean flags.
 *
 * `isRefreshing` is the only flag that may coexist with `Loaded` (pull-
 * to-refresh on an already-loaded list); it lives inside the `Loaded`
 * variant because it's only meaningful in that branch.
 */
data class ChatsScreenViewState(
    /**
     * Load status of the ACTIVE segment. The Chats and Requests segments each
     * have their own list + refresh lifecycle in the VM; [status] always
     * reflects whichever [activeSegment] is showing, so a requests-only failure
     * surfaces as this status only while Requests is active.
     */
    val status: ChatsLoadStatus = ChatsLoadStatus.Loading,
    /** Which segment the user is viewing. */
    val activeSegment: ChatsSegment = ChatsSegment.Chats,
    /**
     * Pending message-request count, used for the badge on the Requests pill.
     * Independent of [activeSegment] so the badge shows while on Chats. `0` = no
     * badge.
     */
    val requestCount: Int = 0,
) : UiState

/** The two top-level segments of the Chats tab home. */
enum class ChatsSegment {
    /** Accepted conversations (`listConvos(status=accepted)`). */
    Chats,

    /** Pending message requests (`listConvos(status=request)`). */
    Requests,
}

sealed interface ChatsLoadStatus {
    /** First load in flight; the body renders a loading composable. */
    data object Loading : ChatsLoadStatus

    /** Convos loaded. May be empty. */
    data class Loaded(
        val items: ImmutableList<ConvoListItemUi> = persistentListOf(),
        val isRefreshing: Boolean = false,
    ) : ChatsLoadStatus

    /** Initial load failed. The body renders an error composable. */
    data class InitialError(
        val error: ChatsError,
    ) : ChatsLoadStatus
}

/**
 * Closed sum of error variants the Chats tab distinguishes for rendering.
 * `NotEnrolled` carries a different empty-state body (and no Retry button)
 * because the user has to act outside the app to fix it.
 */
sealed interface ChatsError {
    data object Network : ChatsError

    data object NotEnrolled : ChatsError

    data class Unknown(
        val cause: String?,
    ) : ChatsError
}

/**
 * UI-ready model for a single conversation row.
 *
 * `lastMessageSnippet == null` only when there are no messages in the convo
 * yet (a brand-new conversation surfaced via `listConvos` before any
 * message has been sent). The row renders an em-dash in that case.
 *
 * `lastMessageFromViewer = true` causes the snippet to render with a
 * "You: " prefix. `lastMessageIsAttachment = true` causes the snippet to
 * render in italic (matches GChat's "Sent an image"); the italic style
 * is composed in the UI layer, not encoded in the snippet string.
 *
 * `sentAt` is the wire timestamp of the last message (null when the convo
 * has no messages yet). The row composable runs it through
 * `rememberChatRelativeTimeText` so the rendered label is localized via
 * `LocalConfiguration` resources and ticks live as time passes. Keeping
 * the raw `Instant` here (instead of a pre-formatted string baked at
 * fetch time) is what makes both behaviors work — see nubecita-nn3.3.
 */
@Immutable
data class ConvoListItemUi(
    val convoId: String,
    val otherUserDid: String,
    val otherUserHandle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val avatarHue: Int,
    val lastMessageSnippet: String?,
    val lastMessageFromViewer: Boolean,
    val lastMessageIsAttachment: Boolean,
    val sentAt: Instant?,
    /**
     * Server-reported unread message count for the viewer in this convo
     * (`chat.bsky.convo.ConvoView.unreadCount`). `0` means fully read.
     * Drives the per-row unread indicator and feeds the Chats-tab badge
     * aggregate. Defaulted so fixture/preview/cache-patch construction
     * sites don't need updating — only the mapper sets a real value.
     */
    val unreadCount: Int = 0,
    /**
     * Whether the viewer has muted this convo. Muted convos still show
     * their unread count in-row but are EXCLUDED from the Chats-tab badge
     * aggregate (a muted thread shouldn't light up the bottom nav).
     */
    val muted: Boolean = false,
)

sealed interface ChatsEvent : UiEvent {
    data object Refresh : ChatsEvent

    data class ConvoTapped(
        val otherUserDid: String,
    ) : ChatsEvent

    data object RetryClicked : ChatsEvent

    /** User tapped the inbox toolbar's settings gear. */
    data object SettingsTapped : ChatsEvent

    /** User selected a segment in the Chats/Requests toggle. */
    data class SegmentSelected(
        val segment: ChatsSegment,
    ) : ChatsEvent
}

sealed interface ChatsEffect : UiEffect {
    data class NavigateToChat(
        val otherUserDid: String,
    ) : ChatsEffect

    /** Push the account-global chat settings route onto the back stack. */
    data object NavigateToChatSettings : ChatsEffect

    /**
     * Sticky errors are rendered inline via `ChatsLoadStatus.InitialError`,
     * not via snackbar. This effect surface is reserved for refresh-time
     * errors that should be transient (Loaded → refresh fails → snackbar).
     */
    data class ShowRefreshError(
        val error: ChatsError,
    ) : ChatsEffect
}
