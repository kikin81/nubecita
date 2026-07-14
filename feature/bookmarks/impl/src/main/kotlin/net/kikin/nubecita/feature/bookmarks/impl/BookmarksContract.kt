package net.kikin.nubecita.feature.bookmarks.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.PostUi

/**
 * MVI state for the Bookmarks list.
 *
 * [loadStatus] is the mutually-exclusive lifecycle sum — per CLAUDE.md's
 * MVI carve-out, it stays sealed so the type system forbids invalid
 * combinations like "InitialLoading AND Loaded.isAppending".
 */
@Immutable
internal data class BookmarksState(
    val loadStatus: BookmarksLoadStatus = BookmarksLoadStatus.InitialLoading,
) : UiState

/**
 * Mutually-exclusive load lifecycle. The list always fetches on open
 * (bookmarks belong to the signed-in user; there is no query to gate on),
 * so the default state is [InitialLoading], not an Idle variant.
 *
 * - [InitialLoading]: first-page fetch.
 * - [Loaded]: results in hand; [items] / [nextCursor] / [endReached] track
 *   pagination; [isAppending] is the transient pagination-in-flight flag.
 * - [Empty]: the user has no bookmarks.
 * - [InitialError]: full-screen retry layout against the typed error.
 */
internal sealed interface BookmarksLoadStatus {
    @Immutable
    data object InitialLoading : BookmarksLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<PostUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : BookmarksLoadStatus

    @Immutable
    data object Empty : BookmarksLoadStatus

    @Immutable
    data class InitialError(
        val error: BookmarksError,
    ) : BookmarksLoadStatus
}

internal sealed interface BookmarksEvent : UiEvent {
    /** Append-on-scroll. Single-flight + idempotent past `endReached`. */
    data object LoadMore : BookmarksEvent

    /** Re-run the initial fetch after [BookmarksLoadStatus.InitialError]. */
    data object Retry : BookmarksEvent

    /** Tap on a post card body → open the thread. */
    data class PostTapped(
        val uri: String,
    ) : BookmarksEvent

    /** Tap on a post author avatar / display name → open their profile. */
    data class OnAuthorTapped(
        val handle: String,
    ) : BookmarksEvent
}

internal sealed interface BookmarksEffect : UiEffect {
    /** Push [net.kikin.nubecita.feature.postdetail.api.PostDetailRoute] onto the MainShell nav stack. */
    data class NavigateToPost(
        val uri: String,
    ) : BookmarksEffect

    /** Push the Profile route for [handle] onto the MainShell nav stack. */
    data class NavigateToProfile(
        val handle: String,
    ) : BookmarksEffect

    /** Snackbar-surface for append-time failures; existing results stay visible. */
    data class ShowAppendError(
        val error: BookmarksError,
    ) : BookmarksEffect
}
