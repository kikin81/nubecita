package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * MVI state for the Hashtag screen.
 *
 * [tag] / [author] come from the route and never change. [authorFilterOn]
 * narrows both tabs to [author]'s posts (only meaningful when [author] is
 * non-null). Each sort tab owns an independent [HashtagTabState] so switching
 * tabs keeps the other tab's results and scroll position.
 */
@Immutable
internal data class HashtagState(
    val tag: String,
    val author: String? = null,
    val authorFilterOn: Boolean = false,
    val selectedSort: SearchPostsSort = SearchPostsSort.TOP,
    val top: HashtagTabState = HashtagTabState(),
    val latest: HashtagTabState = HashtagTabState(),
) : UiState {
    fun tab(sort: SearchPostsSort): HashtagTabState =
        when (sort) {
            SearchPostsSort.TOP -> top
            SearchPostsSort.LATEST -> latest
        }

    fun withTab(
        sort: SearchPostsSort,
        tab: HashtagTabState,
    ): HashtagState =
        when (sort) {
            SearchPostsSort.TOP -> copy(top = tab)
            SearchPostsSort.LATEST -> copy(latest = tab)
        }
}

/**
 * One sort tab. Reuses the Posts-search lifecycle sum ([SearchPostsLoadStatus]:
 * Idle → InitialLoading → Loaded / Empty / InitialError) — Idle means "not
 * loaded yet". [isRefreshing] is independent of the status: a pull-to-refresh
 * keeps the current results visible until the fresh page lands.
 */
@Immutable
internal data class HashtagTabState(
    val status: SearchPostsLoadStatus = SearchPostsLoadStatus.Idle,
    val isRefreshing: Boolean = false,
)

internal sealed interface HashtagEvent : UiEvent {
    /** Top / Latest tab selected. Loads that tab on first selection. */
    data class TabSelected(
        val sort: SearchPostsSort,
    ) : HashtagEvent

    /** "From @author" chip toggled. Resets both tabs and reloads the selected one. */
    data object AuthorFilterToggled : HashtagEvent

    /** Append-on-scroll for the selected tab. Single-flight, idempotent past the end. */
    data object LoadMore : HashtagEvent

    /** Pull-to-refresh on the selected tab. */
    data object Refresh : HashtagEvent

    /** Re-run the first page after an initial error on the selected tab. */
    data object Retry : HashtagEvent

    /** Share action in the top bar. */
    data object ShareClicked : HashtagEvent

    data class PostTapped(
        val uri: String,
    ) : HashtagEvent

    /** Author avatar / name, or a mention facet (DID). */
    data class OnAuthorTapped(
        val actor: String,
    ) : HashtagEvent

    /** A `#tag` inside a result — opens that tag's own screen. */
    data class TagTapped(
        val tag: String,
        val author: String?,
    ) : HashtagEvent
}

internal sealed interface HashtagEffect : UiEffect {
    data class NavigateToPost(
        val uri: String,
    ) : HashtagEffect

    data class NavigateToProfile(
        val actor: String,
    ) : HashtagEffect

    data class NavigateToHashtag(
        val tag: String,
        val author: String?,
    ) : HashtagEffect

    /** Open the system share sheet with [url]. */
    data class Share(
        val url: String,
    ) : HashtagEffect

    /** Append / refresh failure; existing results stay visible. */
    data class ShowError(
        val error: SearchPostsError,
    ) : HashtagEffect
}
