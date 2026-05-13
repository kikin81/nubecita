package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

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
    val status: ChatsLoadStatus = ChatsLoadStatus.Loading,
) : UiState

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
    val timestampRelative: String,
)

sealed interface ChatsEvent : UiEvent {
    data object Refresh : ChatsEvent

    data class ConvoTapped(
        val otherUserDid: String,
    ) : ChatsEvent

    data object RetryClicked : ChatsEvent
}

sealed interface ChatsEffect : UiEffect {
    data class NavigateToChat(
        val otherUserDid: String,
    ) : ChatsEffect

    /**
     * Sticky errors are rendered inline via `ChatsLoadStatus.InitialError`,
     * not via snackbar. This effect surface is reserved for refresh-time
     * errors that should be transient (Loaded → refresh fails → snackbar).
     */
    data class ShowRefreshError(
        val error: ChatsError,
    ) : ChatsEffect
}
