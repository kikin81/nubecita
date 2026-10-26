package net.kikin.nubecita.feature.settings.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.preferences.WatchFeedsPreference
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import timber.log.Timber
import javax.inject.Inject

/**
 * Backs Settings → Watch: the user's pinned feeds as checkboxes, at most
 * [WatchFeedsPreference.MAX_FEEDS] selected, never zero (an empty watch would
 * just say "nothing here").
 *
 * The options are the pinned feeds, with Following added at the top if the
 * user has unpinned it — it's the default watch feed and must stay choosable.
 * Checked state is a pure projection of the stored preference, never local
 * optimistic state.
 */
@HiltViewModel
internal class WatchSettingsViewModel
    @Inject
    constructor(
        pinnedFeedsRepository: PinnedFeedsRepository,
        private val watchFeedsPreference: WatchFeedsPreference,
    ) : MviViewModel<WatchSettingsState, WatchSettingsEvent, WatchSettingsEffect>(WatchSettingsState()) {
        init {
            combine(pinnedFeedsRepository.observePinnedFeeds(), watchFeedsPreference.selectedFeedUris) { pinned, selected ->
                val options = pinned.feeds.toOptions()
                val available = options.mapTo(mutableSetOf()) { it.uri }
                WatchSettingsState(
                    feeds = options.toImmutableList(),
                    selectedUris = selected.filter { it in available }.toImmutableSet(),
                )
            }.onEach { state -> setState { state } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: WatchSettingsEvent) {
            when (event) {
                is WatchSettingsEvent.FeedToggled -> toggle(event.uri, event.selected)
            }
        }

        private fun toggle(
            uri: String,
            selected: Boolean,
        ) {
            val state = uiState.value
            // The UI disables rows that can't change, but enforce the rules here
            // too so a stale tap (or a future control) can't break them.
            if (selected == (uri in state.selectedUris) || !state.isToggleable(uri)) return
            val next = if (selected) state.selectedUris + uri else state.selectedUris - uri
            viewModelScope.launch {
                try {
                    watchFeedsPreference.setSelectedFeedUris(next)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.w(error, "Failed to persist the watch feed selection")
                    sendEffect(WatchSettingsEffect.ShowSaveError)
                }
            }
        }

        private fun List<PinnedFeedUi>.toOptions(): List<WatchFeedOption> {
            val options = map { WatchFeedOption(uri = it.uri, kind = it.kind, displayName = it.displayName, avatarUrl = it.avatarUrl) }
            if (options.any { it.uri == PinnedFeedsRepository.FOLLOWING_FEED_URI }) return options
            val following =
                WatchFeedOption(
                    uri = PinnedFeedsRepository.FOLLOWING_FEED_URI,
                    kind = FeedKind.Following,
                    displayName = "",
                    avatarUrl = null,
                )
            return listOf(following) + options
        }
    }
