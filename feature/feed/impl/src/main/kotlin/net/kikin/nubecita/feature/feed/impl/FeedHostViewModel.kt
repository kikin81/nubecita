package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
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
class FeedHostViewModel
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
                // The persisted-selection read is INSIDE the runCatching with
                // the directory load: DataStore normally always emits, but a
                // corrupt-prefs IOException from `first()` would otherwise
                // escape uncaught and strand the host on Loading with an empty
                // chip set (a permanent blank feed, no error shown). Reading it
                // before the directory still lets a stale URI be validated
                // against the freshly-loaded pinned set.
                runCatching {
                    val persisted = userPreferencesRepository.lastSelectedFeedUri.first()
                    val result = pinnedFeedsRepository.loadPinnedFeeds()
                    persisted to result
                }.onSuccess { (persisted, result) ->
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
                    // The repository is designed never to throw (it returns a
                    // fallback set), so this is the truly-unexpected path
                    // (e.g. a corrupt-prefs read). Populate a usable
                    // Following-only chip + selection so the feed still
                    // renders the timeline instead of a permanent blank
                    // screen, and surface the error once.
                    setState {
                        copy(
                            status = FeedHostStatus.ErrorFallback,
                            feedChips = persistentListOf(FOLLOWING_FALLBACK_CHIP),
                            pinnedLists = persistentListOf(),
                            selectedFeedUri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                        )
                    }
                    sendEffect(FeedHostEffect.ShowError)
                }
            }
        }

        private fun select(uri: String) {
            if (uri == uiState.value.selectedFeedUri) return
            val isValid = (uiState.value.feedChips + uiState.value.pinnedLists).any { it.uri == uri }
            if (!isValid) return
            setState { copy(selectedFeedUri = uri) }
            viewModelScope.launch {
                userPreferencesRepository.setLastSelectedFeedUri(uri)
            }
        }

        private companion object {
            /**
             * Last-resort chip used only on the unexpected `load()` failure
             * path so the host always has a renderable feed. Mirrors the
             * `:core:feeds` Following sentinel: the Following timeline has no
             * `at://` URI, so its id/uri are the [PinnedFeedsRepository.FOLLOWING_FEED_URI]
             * token and the display name is the same literal the repository's
             * own fallback uses (a plain data field, not a string resource).
             */
            val FOLLOWING_FALLBACK_CHIP =
                PinnedFeedUi(
                    id = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                    uri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                    kind = FeedKind.Following,
                    displayName = "Following",
                    avatarUrl = null,
                )
        }
    }
