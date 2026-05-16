package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi

/**
 * MVI state for the Search Feeds tab.
 *
 * [currentQuery] mirrors the latest non-debounced query set via
 * [SearchFeedsViewModel.setQuery]. Held here so the empty-state copy
 * has a stable, recompose-friendly source. Unlike the Posts and People
 * tabs, the Feeds tab does NOT use the query for match-highlighting on
 * the row — the upstream `getPopularFeedGenerators` lexicon doesn't
 * promise that hits actually contain the query substring (results are
 * a globally-popular feed list filtered by query as a hint, not a
 * strict substring match). Empty-state copy is the only consumer.
 *
 * [loadStatus] is the mutually-exclusive lifecycle sum — per CLAUDE.md's
 * MVI carve-out, this stays sealed so the type system forbids
 * combinations like "InitialLoading AND Loaded.isAppending".
 */
@Immutable
internal data class SearchFeedsState(
    val currentQuery: String = "",
    val loadStatus: SearchFeedsLoadStatus = SearchFeedsLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle. Mirrors [SearchActorsLoadStatus].
 */
internal sealed interface SearchFeedsLoadStatus {
    @Immutable
    data object Idle : SearchFeedsLoadStatus

    @Immutable
    data object InitialLoading : SearchFeedsLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<FeedGeneratorUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchFeedsLoadStatus

    @Immutable
    data object Empty : SearchFeedsLoadStatus

    @Immutable
    data class InitialError(
        val error: SearchFeedsError,
    ) : SearchFeedsLoadStatus
}

internal sealed interface SearchFeedsEvent : UiEvent {
    /** Append-on-scroll. Single-flight + idempotent past `endReached`. */
    data object LoadMore : SearchFeedsEvent

    /** Re-run the initial fetch after [SearchFeedsLoadStatus.InitialError]. */
    data object Retry : SearchFeedsEvent

    /** Empty-state "Clear search" button. Parent VM clears the field via effect. */
    data object ClearQueryClicked : SearchFeedsEvent

    /**
     * Tap on a feed row. No-op in V1 — there's no
     * `:feature:feeddetail:api` NavKey yet. When it lands, this event
     * gains a [SearchFeedsEffect.NavigateToFeed] emission and the
     * screen Composable collects it the same way the Posts and People
     * tabs handle their nav effects.
     */
    data class FeedTapped(
        val uri: String,
    ) : SearchFeedsEvent
}

internal sealed interface SearchFeedsEffect : UiEffect {
    /** Snackbar-surface for append-time failures; existing results stay visible. */
    data class ShowAppendError(
        val error: SearchFeedsError,
    ) : SearchFeedsEffect

    /**
     * Empty-state CTA. Forwarded up to the parent [SearchViewModel]
     * (via the host screen) which owns the canonical `TextFieldState`.
     */
    data object NavigateToClearQuery : SearchFeedsEffect
}
