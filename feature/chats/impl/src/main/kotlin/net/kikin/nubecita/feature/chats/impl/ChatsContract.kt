package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import androidx.navigation3.runtime.NavKey
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
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
    /**
     * Multi-select mode: the set of selected convoIds, or `null` when not
     * selecting. Drives the contextual app bar (`selection != null`); the
     * available actions are DERIVED from `selection.size` × [activeSegment] so
     * invalid combinations are unrepresentable. Coexists with `status.Loaded`.
     */
    val selection: ImmutableSet<String>? = null,
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

    /** Long-press a row → enter selection mode with that convo selected. */
    data class ConvoLongPressed(
        val convoId: String,
    ) : ChatsEvent

    /** Tap a row while selecting → toggle its membership. */
    data class SelectionToggled(
        val convoId: String,
    ) : ChatsEvent

    /** Exit selection mode (close affordance / back). */
    data object ClearSelection : ChatsEvent

    /** Contextual-bar actions. Leave/Mute/Accept work for 1..N selected; */
    data object LeaveSelected : ChatsEvent

    /**
     * User tapped Undo on the leave snackbar. Carries the [token] of the batch
     * the snackbar was showing for; a stale tap (token != the current pending
     * batch, e.g. after a supersede) is ignored by the VM.
     */
    data class UndoLeaveTapped(
        val token: Long,
    ) : ChatsEvent

    data object ToggleMuteSelected : ChatsEvent

    data object AcceptSelected : ChatsEvent

    /** Profile/Report/Block are single-select only (the overflow hides at 2+). */
    data object ProfileSelected : ChatsEvent

    data object ReportSelected : ChatsEvent

    data object BlockSelected : ChatsEvent
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

    /**
     * Push a tab-internal sub-route (Profile / Report / Block) onto MainShell's
     * inner back stack. The VM builds the [NavKey]; the screen forwards it to its
     * `onNavigateTo` callback (wired to `navState.add` by the entry provider),
     * mirroring the Feed overflow pattern.
     */
    data class NavigateTo(
        val key: NavKey,
    ) : ChatsEffect

    /** A contextual action (leave / mute / accept) failed at the network. */
    data class ShowActionError(
        val error: ChatsError,
    ) : ChatsEffect

    /**
     * Show the leave-with-undo snackbar after an optimistic leave. The rows are
     * already hidden from the list; [count] picks singular/plural copy and
     * [token] identifies this pending batch so the Undo action can echo it back
     * (stale taps from a superseded batch are dropped). The VM owns the dismiss
     * timer and the deferred `leaveConvo` commit — the snackbar is the Undo
     * affordance, not the commit trigger.
     */
    data class ShowLeaveUndo(
        val token: Long,
        val count: Int,
    ) : ChatsEffect

    /**
     * Dismiss the leave-undo snackbar — emitted when the VM commits the pending
     * leave on its own timer (the undo window elapsed), so the affordance
     * disappears in lock-step with the now-irreversible commit.
     */
    data object HideLeaveUndo : ChatsEffect
}
