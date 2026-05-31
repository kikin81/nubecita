package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.data.models.FeedKind
import javax.inject.Inject

/**
 * Thin host presenter for the feed switcher. Owns the pinned-feed chip
 * list and the current selection only — it MUST NOT own per-feed timeline
 * state (that lives on each retained [FeedViewModel] keyed by `feedUri`).
 *
 * On init it loads the pinned-feed directory and restores the
 * last-selected feed (validated against the live pinned set, else
 * Following). [FeedHostEvent.SelectFeed] / [FeedHostEvent.SelectList]
 * switch the active feed and persist the choice so it survives relaunch.
 */
@HiltViewModel
internal class FeedHostViewModel
    @Inject
    constructor(
        private val pinnedFeedsRepository: PinnedFeedsRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : MviViewModel<FeedHostState, FeedHostEvent, FeedHostEffect>(FeedHostState()) {
        init {
            load()
        }

        override fun handleEvent(event: FeedHostEvent) {
            when (event) {
                FeedHostEvent.Load -> load()
                FeedHostEvent.Retry -> load()
                is FeedHostEvent.SelectFeed -> select(event.uri)
                is FeedHostEvent.SelectList -> select(event.uri)
            }
        }

        private fun load() {
            setState { copy(status = FeedHostStatus.Loading) }
            viewModelScope.launch {
                // Read the persisted selection before the directory so a
                // stale URI can be validated against the freshly-loaded set.
                val persisted = userPreferencesRepository.lastSelectedFeedUri.first()
                runCatching { pinnedFeedsRepository.loadPinnedFeeds() }
                    .onSuccess { result ->
                        // Lists collapse behind one disclosure chip (a580.8);
                        // feeds (Following/Generator) stay individual chips.
                        val lists = result.feeds.filter { it.kind == FeedKind.List }
                        val chips = result.feeds.filter { it.kind != FeedKind.List }
                        val selected =
                            pinnedFeedsRepository.validateSelectedFeedUri(persisted, result.feeds)
                        setState {
                            copy(
                                status =
                                    if (result.usedFallback) {
                                        FeedHostStatus.ErrorFallback
                                    } else {
                                        FeedHostStatus.Ready
                                    },
                                feedChips = chips.toImmutableList(),
                                pinnedLists = lists.toImmutableList(),
                                selectedFeedUri = selected,
                            )
                        }
                        if (result.usedFallback) {
                            sendEffect(FeedHostEffect.ShowError)
                        }
                    }.onFailure {
                        // The repository is designed not to throw (it returns
                        // a fallback set), but guard defensively: surface the
                        // error and let the host fall back to Following.
                        setState { copy(status = FeedHostStatus.ErrorFallback) }
                        sendEffect(FeedHostEffect.ShowError)
                    }
            }
        }

        private fun select(uri: String) {
            if (uri == uiState.value.selectedFeedUri) return
            setState { copy(selectedFeedUri = uri) }
            viewModelScope.launch {
                userPreferencesRepository.setLastSelectedFeedUri(uri)
            }
        }
    }
