package net.kikin.nubecita.feature.feed.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.PinnedFeedUi

/**
 * State for the feed switcher host. Holds ONLY the chip list and the
 * current selection — never per-feed timeline state (posts, cursor,
 * pagination). Each feed's timeline lives on its own retained
 * [FeedViewModel], obtained per `feedUri` inside [FeedHost].
 *
 * @property status the mutually-exclusive host lifecycle.
 * @property feedChips the pinned feeds rendered as individual chips
 *   (`Following` + `Generator`), in pinned order.
 * @property pinnedLists the pinned lists, collapsed behind a single
 *   disclosure chip by the chip row (built in a580.8).
 * @property selectedFeedUri the URI of the active feed/list, or null
 *   before the first load resolves.
 */
@Immutable
data class FeedHostState(
    val status: FeedHostStatus = FeedHostStatus.Loading,
    val feedChips: ImmutableList<PinnedFeedUi> = persistentListOf(),
    val pinnedLists: ImmutableList<PinnedFeedUi> = persistentListOf(),
    val selectedFeedUri: String? = null,
) : UiState

/**
 * The host's mutually-exclusive lifecycle. [ErrorFallback] still carries
 * a usable chip set (the `[Following, Discover]` fallback) — it signals
 * "we couldn't read your pinned feeds, showing defaults", not a dead end.
 */
sealed interface FeedHostStatus {
    data object Loading : FeedHostStatus

    data object Ready : FeedHostStatus

    data object ErrorFallback : FeedHostStatus
}

sealed interface FeedHostEvent : UiEvent {
    data object Load : FeedHostEvent

    data object Retry : FeedHostEvent

    data class SelectFeed(
        val uri: String,
    ) : FeedHostEvent

    data class SelectList(
        val uri: String,
    ) : FeedHostEvent
}

sealed interface FeedHostEffect : UiEffect {
    /**
     * Non-sticky notice that the pinned-feed directory couldn't be loaded
     * and the Feed fell back to the default chips. Carries no message — the
     * VM stays Android-resource-free; [FeedHost] resolves the copy via
     * `stringResource` (matching the sibling `FeedViewModel` error pattern).
     */
    data object ShowError : FeedHostEffect
}
