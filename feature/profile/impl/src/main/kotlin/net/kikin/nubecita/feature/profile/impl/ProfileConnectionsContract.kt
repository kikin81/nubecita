package net.kikin.nubecita.feature.profile.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.feature.profile.api.ProfileConnectionsTab
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi

/**
 * What the viewer has done to one account on this screen, keyed by DID in
 * [ProfileConnectionsState.overlay].
 *
 * Kept apart from the paged rows on purpose. The rows come from the server,
 * which can report the old state for a moment after a write, so a page reload
 * must not undo what the viewer just did. And an account can appear in both
 * tabs, so the state belongs to the account rather than to a row.
 */
@Immutable
internal sealed interface ConnectionFollowState {
    /** A write is in flight, heading towards [following]. */
    data class Pending(
        val following: Boolean,
    ) : ConnectionFollowState

    /** The server accepted the write. [followUri] is null once unfollowed. */
    data class Committed(
        val followUri: String?,
    ) : ConnectionFollowState
}

/** What a row's button shows. */
@Immutable
internal data class ConnectionFollowDisplay(
    val following: Boolean,
    val pending: Boolean,
)

/** This row as [state] leaves it; the overlay outranks what the page reported. */
internal fun ConnectionRowUi.followDisplay(state: ConnectionFollowState?): ConnectionFollowDisplay =
    when (state) {
        null -> ConnectionFollowDisplay(following = followUri != null, pending = false)
        is ConnectionFollowState.Pending -> ConnectionFollowDisplay(following = state.following, pending = true)
        is ConnectionFollowState.Committed -> ConnectionFollowDisplay(following = state.followUri != null, pending = false)
    }

internal data class ProfileConnectionsState(
    val selectedTab: ProfileConnectionsTab,
    val title: String,
    val followersCount: Long,
    val followsCount: Long,
    val overlay: PersistentMap<String, ConnectionFollowState> = persistentMapOf(),
) : UiState

internal sealed interface ProfileConnectionsEvent : UiEvent {
    data class TabSelected(
        val tab: ProfileConnectionsTab,
    ) : ProfileConnectionsEvent

    data class FollowTapped(
        val row: ConnectionRowUi,
    ) : ProfileConnectionsEvent

    data class RowTapped(
        val row: ConnectionRowUi,
    ) : ProfileConnectionsEvent
}

internal sealed interface ProfileConnectionsEffect : UiEffect {
    data class NavigateToProfile(
        val handle: String,
    ) : ProfileConnectionsEffect

    data object ShowError : ProfileConnectionsEffect
}
