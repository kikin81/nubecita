package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.FeedAction
import net.kikin.nubecita.core.analytics.InteractFeed
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.feature.feed.api.FeedView

/**
 * Pin-state holder for [FeedViewScreen].
 *
 * Observes whether the current [feedUri] is pinned to the tab bar via
 * [PinnedFeedsRepository.observePinnedFeeds] and exposes a toggle
 * that calls [PinnedFeedsRepository.pinFeed] / [PinnedFeedsRepository.unpinFeed].
 * Analytics ([InteractFeed]) are fired on every successful toggle.
 * Failures surface as [FeedPinEffect.ShowError] for the screen to display
 * as a transient snackbar.
 *
 * Uses Hilt's assisted-injection bridge so [feedUri] flows from the
 * [FeedView] entry-provider call site into the constructor without a
 * SavedStateHandle decode step — the same pattern as [PostDetailViewModel].
 */
@HiltViewModel(assistedFactory = FeedPinViewModel.Factory::class)
internal class FeedPinViewModel
    @AssistedInject
    constructor(
        @Assisted private val feedUri: String,
        private val pinnedFeedsRepository: PinnedFeedsRepository,
        private val analytics: AnalyticsClient,
    ) : MviViewModel<FeedPinState, FeedPinEvent, FeedPinEffect>(FeedPinState()) {
        @AssistedFactory
        interface Factory {
            fun create(feedUri: String): FeedPinViewModel
        }

        init {
            viewModelScope.launch {
                pinnedFeedsRepository
                    .observePinnedFeeds()
                    .map { result -> result.feeds.any { it.uri == feedUri } }
                    .collect { pinned -> setState { copy(isPinned = pinned) } }
            }
        }

        override fun handleEvent(event: FeedPinEvent) {
            when (event) {
                FeedPinEvent.TogglePin -> togglePin()
            }
        }

        private fun togglePin() {
            val wasPinned = uiState.value.isPinned
            viewModelScope.launch {
                val result =
                    if (wasPinned) {
                        pinnedFeedsRepository.unpinFeed(feedUri)
                    } else {
                        pinnedFeedsRepository.pinFeed(feedUri)
                    }
                result
                    .onSuccess {
                        analytics.log(
                            InteractFeed(
                                action = if (wasPinned) FeedAction.Unpin else FeedAction.Pin,
                                surface = PostSurface.FeedView,
                            ),
                        )
                    }.onFailure {
                        sendEffect(FeedPinEffect.ShowError)
                    }
            }
        }
    }

data class FeedPinState(
    val isPinned: Boolean = false,
) : UiState

sealed interface FeedPinEvent : UiEvent {
    data object TogglePin : FeedPinEvent
}

sealed interface FeedPinEffect : UiEffect {
    /** Non-sticky pin/unpin failure — surface as a transient snackbar. */
    data object ShowError : FeedPinEffect
}
