package net.kikin.nubecita.feature.starterpack.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackDetail

/**
 * MVI state for the starter-pack screen.
 *
 * [status] is the mutually-exclusive load lifecycle, a per-screen sealed sum. [viewerDid] is
 * the signed-in account, so the screen draws no Follow action on that account's own row.
 */
@Immutable
internal data class StarterPackState(
    val status: StarterPackLoadStatus = StarterPackLoadStatus.InitialLoading,
    val viewerDid: String? = null,
) : UiState

internal sealed interface StarterPackLoadStatus {
    @Immutable
    data object InitialLoading : StarterPackLoadStatus

    /** The pack and at least its first page of members. */
    @Immutable
    data class Loaded(
        val pack: StarterPackDetail,
        val members: ImmutableList<ActorUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : StarterPackLoadStatus

    /** The pack loaded and has no members. The header still shows. */
    @Immutable
    data class Empty(
        val pack: StarterPackDetail,
    ) : StarterPackLoadStatus

    @Immutable
    data class InitialError(
        val error: StarterPackError,
    ) : StarterPackLoadStatus
}

internal sealed interface StarterPackEvent : UiEvent {
    /** Append-on-scroll. Single-flight, and a no-op past the end. */
    data object LoadMore : StarterPackEvent

    /** Re-run the initial load after [StarterPackLoadStatus.InitialError]. */
    data object Retry : StarterPackEvent

    /** Tap on a member row: opens their profile. */
    data class MemberTapped(
        val actor: String,
    ) : StarterPackEvent

    /** Tap on one of the pack's feeds: opens it. */
    data class FeedTapped(
        val uri: String,
        val displayName: String?,
    ) : StarterPackEvent

    /** Follow / Following on a member row. */
    data class FollowTapped(
        val did: String,
    ) : StarterPackEvent
}

internal sealed interface StarterPackEffect : UiEffect {
    /** Snackbar for a failed next page; the members already shown stay. */
    data class ShowAppendError(
        val error: StarterPackError,
    ) : StarterPackEffect

    /** A follow or unfollow failed and the row was put back. */
    data class FollowFailed(
        val wasUnfollow: Boolean,
    ) : StarterPackEffect

    data class NavigateToProfile(
        val actor: String,
    ) : StarterPackEffect

    data class NavigateToFeed(
        val uri: String,
        val displayName: String?,
    ) : StarterPackEffect
}
