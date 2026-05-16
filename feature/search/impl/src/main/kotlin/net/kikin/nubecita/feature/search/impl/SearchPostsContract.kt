package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * MVI state for the Search Posts tab.
 *
 * [currentQuery] mirrors the latest non-debounced query set via
 * [SearchPostsViewModel.setQuery]. Held here (rather than re-derived
 * from the parent) so the empty-state copy + body match-highlighting
 * have a stable, recompose-friendly source.
 *
 * [sort] is the active sort mode. Defaults to [SearchPostsSort.TOP];
 * changing it via [SearchPostsEvent.SortClicked] resets pagination
 * and triggers a fresh first-page fetch.
 *
 * [loadStatus] is the mutually-exclusive lifecycle sum — per CLAUDE.md's
 * MVI carve-out, this stays sealed so the type system forbids
 * combinations like "InitialLoading AND Loaded.isAppending".
 */
@Immutable
internal data class SearchPostsState(
    val currentQuery: String = "",
    val sort: SearchPostsSort = SearchPostsSort.TOP,
    val loadStatus: SearchPostsLoadStatus = SearchPostsLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle. Five variants, each rendered by a
 * distinct body composable in [net.kikin.nubecita.feature.search.impl.ui.PostsTabContent].
 *
 * - [Idle]: query is blank; render nothing (parent's recent-search chips show).
 * - [InitialLoading]: first-page fetch for the current query.
 * - [Loaded]: results in hand; [items], [nextCursor], [endReached] track
 *   pagination; [isAppending] is the transient pagination-in-flight flag.
 * - [Empty]: query returned zero results.
 * - [InitialError]: full-screen retry layout against the typed error.
 */
internal sealed interface SearchPostsLoadStatus {
    @Immutable
    data object Idle : SearchPostsLoadStatus

    @Immutable
    data object InitialLoading : SearchPostsLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<FeedItemUi.Single>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchPostsLoadStatus

    @Immutable
    data object Empty : SearchPostsLoadStatus

    @Immutable
    data class InitialError(
        val error: SearchPostsError,
    ) : SearchPostsLoadStatus
}

internal sealed interface SearchPostsEvent : UiEvent {
    /** Append-on-scroll. Single-flight + idempotent past `endReached`. */
    data object LoadMore : SearchPostsEvent

    /** Re-run the initial fetch after [SearchPostsLoadStatus.InitialError]. */
    data object Retry : SearchPostsEvent

    /** Empty-state "Clear search" button. Parent VM clears the field via effect. */
    data object ClearQueryClicked : SearchPostsEvent

    /** Sort chip / empty-state "Change sort" button. Resets pagination + refetches. */
    data class SortClicked(
        val sort: SearchPostsSort,
    ) : SearchPostsEvent

    /** Tap on a post card. Emits [SearchPostsEffect.NavigateToPost]. */
    data class PostTapped(
        val uri: String,
    ) : SearchPostsEvent
}

internal sealed interface SearchPostsEffect : UiEffect {
    /** Push [net.kikin.nubecita.feature.postdetail.api.PostDetailRoute] onto the MainShell nav stack. */
    data class NavigateToPost(
        val uri: String,
    ) : SearchPostsEffect

    /** Snackbar-surface for append-time failures; existing results stay visible. */
    data class ShowAppendError(
        val error: SearchPostsError,
    ) : SearchPostsEffect

    /**
     * Empty-state CTA dispatched up to vrba.8's screen; vrba.8 forwards
     * to the parent [SearchViewModel] which owns the canonical
     * `TextFieldState`. SearchPostsViewModel can't reach the parent VM
     * directly (MVI rule: no Hilt ViewModel-into-ViewModel injection).
     */
    data object NavigateToClearQuery : SearchPostsEffect
}
