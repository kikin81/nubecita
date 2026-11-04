package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.Immutable
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.OutgoingPostUi

/**
 * Screen-private projection of the feed's state into the four
 * mutually-exclusive render branches that `FeedScreen`'s host `when` switches
 * over.
 *
 * Lives inside `:feature:feed:impl` and never crosses the VM/UI boundary. The
 * items themselves are NOT here: they stay in `LazyPagingItems` and are handed
 * to the list composable directly, because copying a windowed, paged list into
 * a state object would reintroduce the unbounded in-memory list the cache
 * exists to replace (D1).
 */
@Immutable
sealed interface FeedScreenViewState {
    /** Initial load with no items yet — render shimmer rows. */
    @Immutable data object InitialLoading : FeedScreenViewState

    /** Settled with no items — render `FeedEmptyState`. */
    @Immutable data object Empty : FeedScreenViewState

    /** Initial load failed and no items to fall back on — render full-screen retry. */
    @Immutable data class InitialError(
        val error: FeedError,
    ) : FeedScreenViewState

    /**
     * Items are present.
     *
     * [isAppending] toggles the tail shimmer; [isRefreshing] drives
     * `PullToRefreshBox`'s indicator. They are mutually exclusive in practice —
     * [FeedLoadStatus] makes `Refreshing` and `Appending` unrepresentable
     * together, so a `Loaded(isAppending = true, isRefreshing = true)` never
     * reaches the screen.
     */
    @Immutable
    data class Loaded(
        val isAppending: Boolean,
        val isRefreshing: Boolean,
        val lastLikeTapPostUri: String? = null,
        val lastRepostTapPostUri: String? = null,
        /**
         * Mirrors `FeedState.videoAutoplayEnabled`. Defaulted so the many
         * preview and test constructions of this branch keep describing the
         * ordinary autoplaying feed without restating it.
         */
        val videoAutoplayEnabled: Boolean = true,
        /** Posts the user just submitted, rendered above the paged items. */
        val outgoingPosts: ImmutableList<OutgoingPostUi> = persistentListOf(),
        /**
         * Mirrors `FeedState.isFillingSeam`. A seam fill is a prepend behind
         * the projection, so Paging's load states say nothing about it.
         */
        val isFillingSeam: Boolean = false,
    ) : FeedScreenViewState
}

/**
 * Collapse Paging's load states into the feed's own lifecycle vocabulary.
 *
 * [itemCount] is what separates a first load from a refresh: `refresh` is
 * `Loading` in both cases, and the difference the reader sees is whether there
 * is anything on screen underneath the spinner. Appending is checked before
 * refreshing because the tail shimmer and the pull indicator are different
 * affordances and only one may show.
 *
 * `refresh` is the COMBINED state, so a mediator fetch over a warm cache — the
 * normal case for this feature — reports here too, rather than looking idle
 * because the local source answered instantly.
 *
 * `prepend` is deliberately unmapped: a `since`-bounded catch-up gets its own
 * affordances — the new-posts pill and the seam row — rather than the tail
 * shimmer or the pull indicator. Mapping it to `Appending` would hang a tail
 * shimmer off the bottom of the list for posts that arrive at the top.
 *
 * A pure function over Paging's own types, so the dispatch matrix is unit-
 * tested without a Compose host or a `Pager`.
 */
fun CombinedLoadStates.toFeedLoadStatus(itemCount: Int): FeedLoadStatus =
    when {
        itemCount == 0 && refresh is LoadState.Loading -> FeedLoadStatus.InitialLoading
        itemCount == 0 && refresh is LoadState.Error ->
            FeedLoadStatus.InitialError((refresh as LoadState.Error).error.toFeedError())
        append is LoadState.Loading -> FeedLoadStatus.Appending
        refresh is LoadState.Loading -> FeedLoadStatus.Refreshing
        else -> FeedLoadStatus.Idle
    }

/**
 * Project the load status and item count to a render branch. Total over both.
 *
 * | `itemCount` | status              | Result                                  |
 * |-------------|---------------------|-----------------------------------------|
 * | 0           | `InitialLoading`    | [FeedScreenViewState.InitialLoading]    |
 * | 0           | `InitialError`      | [FeedScreenViewState.InitialError]      |
 * | 0           | anything else       | [FeedScreenViewState.Empty]             |
 * | > 0         | `Appending`         | [FeedScreenViewState.Loaded] (`isAppending = true`) |
 * | > 0         | `Refreshing`        | [FeedScreenViewState.Loaded] (`isRefreshing = true`) |
 * | > 0         | anything else       | [FeedScreenViewState.Loaded]            |
 *
 * An empty feed never opens on "No posts": [toFeedLoadStatus] only leaves
 * `InitialLoading` once a load settles, so empty + settled unambiguously means
 * "loaded, nothing to show" (lq9t.3.6).
 *
 * The `InitialLoading` / `InitialError` rows with items present cannot occur —
 * [toFeedLoadStatus] gates both on an empty list — and are handled for total
 * coverage so a later change cannot introduce a silent unhandled state.
 */
fun FeedState.toViewState(
    status: FeedLoadStatus,
    itemCount: Int,
): FeedScreenViewState =
    if (itemCount == 0) {
        when (status) {
            FeedLoadStatus.InitialLoading -> FeedScreenViewState.InitialLoading
            is FeedLoadStatus.InitialError -> FeedScreenViewState.InitialError(status.error)
            FeedLoadStatus.Idle,
            FeedLoadStatus.Refreshing,
            FeedLoadStatus.Appending,
            -> FeedScreenViewState.Empty
        }
    } else {
        FeedScreenViewState.Loaded(
            isAppending = status == FeedLoadStatus.Appending,
            isRefreshing = status == FeedLoadStatus.Refreshing,
            lastLikeTapPostUri = lastLikeTapPostUri,
            lastRepostTapPostUri = lastRepostTapPostUri,
            videoAutoplayEnabled = videoAutoplayEnabled,
            outgoingPosts = outgoingPosts,
            isFillingSeam = isFillingSeam,
        )
    }
