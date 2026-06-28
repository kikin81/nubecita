package net.kikin.nubecita.feature.feed.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.preferences.UserPreferencesRepository
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import timber.log.Timber
import javax.inject.Inject

/**
 * Thin host presenter for the feed switcher. Owns the pinned-feed chip
 * list and the current selection only — it MUST NOT own per-feed timeline
 * state (that lives on each retained [FeedViewModel] keyed by `feedUri`).
 *
 * On init it starts observing the Room-cached pinned-feed directory
 * ([PinnedFeedsRepository.observePinnedFeeds]) and kicks off a network
 * refresh ([PinnedFeedsRepository.refresh]) to keep the cache warm.
 * [FeedHostEvent.SelectFeed] / [FeedHostEvent.SelectList] switch the
 * active feed and persist the choice so it survives relaunch.
 * [FeedHostEvent.Retry] triggers a new refresh.
 */
@HiltViewModel
class FeedHostViewModel
    @Inject
    constructor(
        private val pinnedFeedsRepository: PinnedFeedsRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
    ) : MviViewModel<FeedHostState, FeedHostEvent, FeedHostEffect>(FeedHostState()) {
        init {
            observeAndRefresh()
        }

        override fun handleEvent(event: FeedHostEvent) {
            when (event) {
                FeedHostEvent.Load, FeedHostEvent.Retry -> triggerRefresh()
                is FeedHostEvent.SelectFeed -> select(event.uri)
                is FeedHostEvent.SelectList -> select(event.uri)
            }
        }

        /**
         * Reads the persisted feed selection once, then sets up a perpetual
         * observer on the Room cache and concurrently fires the first network
         * refresh. The observer is the single source of truth for [FeedHostState]
         * updates; [refresh] just keeps the cache warm.
         */
        private fun observeAndRefresh() {
            viewModelScope.launch {
                // Read the persisted selection BEFORE starting the flow
                // collector so the first emission can validate against it. A
                // DataStore IOException here is non-fatal — we simply skip
                // restoring the selection (Following is the implicit default).
                val persisted =
                    runCatching {
                        userPreferencesRepository.lastSelectedFeedUri.first()
                    }.getOrNull()

                // Fire the first network refresh concurrently. The DB update it
                // produces will drive a new emission from observePinnedFeeds().
                // Failure is non-fatal — the cache (or fallback) keeps the Feed usable.
                launch { pinnedFeedsRepository.refresh() }

                pinnedFeedsRepository
                    .observePinnedFeeds()
                    .onEach { result -> applyResult(result, persisted) }
                    .catch { throwable ->
                        // observePinnedFeeds() should never throw (Room errors
                        // surface as terminal DB errors — extremely rare). Guard
                        // anyway so the host never stays stuck in Loading.
                        Timber.tag(TAG).e(throwable, "observePinnedFeeds collector failed")
                        applyUnexpectedFailure()
                    }.collect()
            }
        }

        /** Kicks off a background refresh without blocking the UI. */
        private fun triggerRefresh() {
            viewModelScope.launch { pinnedFeedsRepository.refresh() }
        }

        private fun applyResult(
            result: PinnedFeedsResult,
            persistedUri: String?,
        ) {
            // For the first emission (selectedFeedUri is null), validate the
            // persisted URI against the live pinned set. For subsequent emissions
            // (user already made a selection), validate the current state so a
            // refresh that removes a no-longer-pinned feed gracefully falls back.
            val toValidate = uiState.value.selectedFeedUri ?: persistedUri
            val validated =
                pinnedFeedsRepository.validateSelectedFeedUri(toValidate, result.feeds)
            val lists = result.feeds.filter { it.kind == FeedKind.List }
            val chips = result.feeds.filter { it.kind != FeedKind.List }
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
                    selectedFeedUri = validated,
                )
            }
            if (result.usedFallback) sendEffect(FeedHostEffect.ShowError)
        }

        private fun applyUnexpectedFailure() {
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
            private const val TAG = "FeedHostVM"

            /**
             * Last-resort chip used only on the unexpected `observePinnedFeeds()`
             * collector failure path so the host always has a renderable feed.
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
