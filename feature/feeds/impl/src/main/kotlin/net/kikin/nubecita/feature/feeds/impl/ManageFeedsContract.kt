package net.kikin.nubecita.feature.feeds.impl

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.PinnedFeedUi

/**
 * State for the pinned-feeds management screen.
 *
 * [status] is a mutually-exclusive lifecycle (loading vs populated), so it is a
 * sealed sum rather than flat flags (per the MVI conventions). There is no empty
 * variant: the Following timeline is non-removable and the repository falls back
 * to a default pinned set, so the list is never blank.
 */
internal data class ManageFeedsState(
    val status: ManageFeedsLoadStatus = ManageFeedsLoadStatus.Loading,
) : UiState

internal sealed interface ManageFeedsLoadStatus {
    data object Loading : ManageFeedsLoadStatus

    /** [feeds] is the local, drag-mutable order — Compose reorders it instantly; the
     * new order is committed to the repository on screen-exit / app-background. */
    data class Content(
        val feeds: ImmutableList<PinnedFeedUi>,
    ) : ManageFeedsLoadStatus
}

internal sealed interface ManageFeedsEvent : UiEvent {
    /** A drag moved the row at [from] to [to] (0-based indices into the current list). */
    data class Move(
        val from: Int,
        val to: Int,
    ) : ManageFeedsEvent

    /** The trailing remove action was tapped on the feed at [uri] (unpin, non-destructive). */
    data class Remove(
        val uri: String,
    ) : ManageFeedsEvent
}

internal sealed interface ManageFeedsEffect : UiEffect {
    /** Unpin failed — the screen surfaces a snackbar and the row is restored. */
    data object ShowRemoveError : ManageFeedsEffect
}
