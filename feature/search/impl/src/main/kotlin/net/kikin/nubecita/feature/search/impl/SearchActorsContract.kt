package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

/**
 * MVI state for the Search People tab.
 *
 * [currentQuery] mirrors the latest non-debounced query set via
 * [SearchActorsViewModel.setQuery]. Held here (rather than re-derived
 * from the parent) so the empty-state copy + match-highlighting have a
 * stable, recompose-friendly source.
 *
 * [loadStatus] is the mutually-exclusive lifecycle sum — per CLAUDE.md's
 * MVI carve-out, this stays sealed so the type system forbids
 * combinations like "InitialLoading AND Loaded.isAppending".
 */
@Immutable
internal data class SearchActorsState(
    val currentQuery: String = "",
    val loadStatus: SearchActorsLoadStatus = SearchActorsLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle. Five variants, each rendered by a
 * distinct body composable in [net.kikin.nubecita.feature.search.impl.ui.PeopleTabContent].
 *
 * - [Idle]: query is blank; render nothing.
 * - [InitialLoading]: first-page fetch for the current query.
 * - [Loaded]: results in hand; [items], [nextCursor], [endReached] track
 *   pagination; [isAppending] is the transient pagination-in-flight flag.
 * - [Empty]: query returned zero results.
 * - [InitialError]: full-screen retry layout against the typed error.
 */
internal sealed interface SearchActorsLoadStatus {
    @Immutable
    data object Idle : SearchActorsLoadStatus

    @Immutable
    data object InitialLoading : SearchActorsLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<ActorUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchActorsLoadStatus

    @Immutable
    data object Empty : SearchActorsLoadStatus

    @Immutable
    data class InitialError(
        val error: SearchActorsError,
    ) : SearchActorsLoadStatus
}

internal sealed interface SearchActorsEvent : UiEvent {
    /** Append-on-scroll. Single-flight + idempotent past `endReached`. */
    data object LoadMore : SearchActorsEvent

    /** Re-run the initial fetch after [SearchActorsLoadStatus.InitialError]. */
    data object Retry : SearchActorsEvent

    /** Empty-state "Clear search" button. Parent VM clears the field via effect. */
    data object ClearQueryClicked : SearchActorsEvent

    /** Tap on an actor row. Emits [SearchActorsEffect.NavigateToProfile]. */
    data class ActorTapped(
        val handle: String,
    ) : SearchActorsEvent
}

internal sealed interface SearchActorsEffect : UiEffect {
    /** Push [net.kikin.nubecita.feature.profile.api.Profile] onto the MainShell nav stack. */
    data class NavigateToProfile(
        val handle: String,
    ) : SearchActorsEffect

    /** Snackbar-surface for append-time failures; existing results stay visible. */
    data class ShowAppendError(
        val error: SearchActorsError,
    ) : SearchActorsEffect

    /**
     * Empty-state CTA dispatched up to vrba.8's screen; vrba.8 forwards
     * to the parent [SearchViewModel] which owns the canonical
     * `TextFieldState`. SearchActorsViewModel can't reach the parent VM
     * directly (MVI rule: no Hilt ViewModel-into-ViewModel injection).
     */
    data object NavigateToClearQuery : SearchActorsEffect
}
