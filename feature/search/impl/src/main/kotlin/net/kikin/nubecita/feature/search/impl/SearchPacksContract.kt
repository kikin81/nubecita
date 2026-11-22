package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.StarterPackUi

/**
 * MVI state for the Search Packs tab. [currentQuery] mirrors the latest query set via
 * [SearchPacksViewModel.setQuery]; the empty state's copy is its only reader. [loadStatus] is the
 * mutually-exclusive load lifecycle, sealed so the type system forbids "InitialLoading AND
 * appending". Same shape as [SearchFeedsState].
 */
@Immutable
internal data class SearchPacksState(
    val currentQuery: String = "",
    val loadStatus: SearchPacksLoadStatus = SearchPacksLoadStatus.Idle,
) : UiState

internal sealed interface SearchPacksLoadStatus {
    @Immutable
    data object Idle : SearchPacksLoadStatus

    @Immutable
    data object InitialLoading : SearchPacksLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<StarterPackUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchPacksLoadStatus

    @Immutable
    data object Empty : SearchPacksLoadStatus

    @Immutable
    data class InitialError(
        val error: SearchPacksError,
    ) : SearchPacksLoadStatus
}

internal sealed interface SearchPacksEvent : UiEvent {
    /** Append-on-scroll. Single-flight and idempotent past `endReached`. */
    data object LoadMore : SearchPacksEvent

    /** Re-run the initial fetch after [SearchPacksLoadStatus.InitialError]. */
    data object Retry : SearchPacksEvent

    /** Empty-state "Clear search" button. The parent screen clears the field via effect. */
    data object ClearQueryClicked : SearchPacksEvent

    /** Tap on a pack card or row: emits [SearchPacksEffect.NavigateToPack]. */
    data class PackTapped(
        val uri: String,
    ) : SearchPacksEvent
}

internal sealed interface SearchPacksEffect : UiEffect {
    /** Snackbar for a failed next page; existing results stay visible. */
    data class ShowAppendError(
        val error: SearchPacksError,
    ) : SearchPacksEffect

    /** Empty-state CTA, forwarded to the host screen which owns the field. */
    data object NavigateToClearQuery : SearchPacksEffect

    /** Push [net.kikin.nubecita.feature.starterpack.api.StarterPack] onto the MainShell nav stack. */
    data class NavigateToPack(
        val uri: String,
    ) : SearchPacksEffect
}
