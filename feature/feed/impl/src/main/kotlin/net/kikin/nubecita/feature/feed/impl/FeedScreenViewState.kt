package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.PostUi

/**
 * Screen-private projection of [FeedState] into the five mutually-exclusive
 * render branches that `FeedScreen`'s host `when` switches over.
 *
 * Lives inside `:feature:feed:impl` and never crosses the VM/UI boundary —
 * the VM emits `FeedState`, the screen converts to `FeedScreenViewState`,
 * the rendering composable pattern-matches on the result. This keeps the
 * VM contract free of UI rendering concerns and makes each render branch
 * unit-testable independently.
 *
 * Total over [FeedState]: see [toViewState] for the dispatch matrix.
 */
@Immutable
internal sealed interface FeedScreenViewState {
    /** Initial load with no posts yet — render shimmer rows. */
    @Immutable data object InitialLoading : FeedScreenViewState

    /** Idle with no posts — render `FeedEmptyState`. */
    @Immutable data object Empty : FeedScreenViewState

    /** Initial load failed and no posts to fall back on — render full-screen retry. */
    @Immutable data class InitialError(
        val error: FeedError,
    ) : FeedScreenViewState

    /**
     * Posts are present. [isAppending] toggles the tail shimmer;
     * [isRefreshing] drives `PullToRefreshBox`'s indicator. The two
     * are mutually exclusive in practice — [FeedLoadStatus] makes
     * `Refreshing` and `Appending` unrepresentable simultaneously, so a
     * `Loaded(isAppending = true, isRefreshing = true)` value never
     * reaches the screen.
     */
    @Immutable
    data class Loaded(
        val posts: ImmutableList<PostUi>,
        val isAppending: Boolean,
        val isRefreshing: Boolean,
    ) : FeedScreenViewState
}

/**
 * Project [FeedState] to its render branch. Total over `(loadStatus, posts)`.
 *
 * Dispatch is `posts.isEmpty()` first, `loadStatus` second:
 *
 * | `posts`    | `loadStatus`        | Result                        |
 * |------------|---------------------|-------------------------------|
 * | empty      | `InitialLoading`    | [FeedScreenViewState.InitialLoading]    |
 * | empty      | `InitialError`      | [FeedScreenViewState.InitialError]      |
 * | empty      | `Idle`              | [FeedScreenViewState.Empty]             |
 * | empty      | `Refreshing`/`Appending` | [FeedScreenViewState.Empty] (VM-impossible; safe fallback) |
 * | non-empty  | `Appending`         | [FeedScreenViewState.Loaded] (`isAppending = true`)  |
 * | non-empty  | any other           | [FeedScreenViewState.Loaded] (`isAppending = false`) |
 *
 * The `Refreshing` / `Appending` cases with `posts.isEmpty()` and the
 * `InitialLoading` / `InitialError` cases with `posts.isNotEmpty()` are
 * never produced by the VM (see `FeedViewModel`'s reducers — initial
 * states are gated on `posts.isEmpty()`). They're handled here for total
 * coverage so a future contract change can't introduce a silent
 * unhandled-state crash.
 */
internal fun FeedState.toViewState(): FeedScreenViewState =
    if (posts.isEmpty()) {
        when (loadStatus) {
            FeedLoadStatus.InitialLoading -> FeedScreenViewState.InitialLoading
            is FeedLoadStatus.InitialError -> FeedScreenViewState.InitialError(loadStatus.error)
            FeedLoadStatus.Idle,
            FeedLoadStatus.Refreshing,
            FeedLoadStatus.Appending,
            -> FeedScreenViewState.Empty
        }
    } else {
        FeedScreenViewState.Loaded(
            posts = posts,
            isAppending = loadStatus == FeedLoadStatus.Appending,
            isRefreshing = loadStatus == FeedLoadStatus.Refreshing,
        )
    }
