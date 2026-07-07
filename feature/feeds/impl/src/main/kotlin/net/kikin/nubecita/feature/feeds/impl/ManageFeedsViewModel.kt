package net.kikin.nubecita.feature.feeds.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.data.models.PinnedFeedUi
import timber.log.Timber
import javax.inject.Inject

/**
 * Presenter for the pinned-feeds management screen.
 *
 * Compose owns the drag-mutable order: [ManageFeedsEvent.Move] reorders the local
 * [ManageFeedsLoadStatus.Content.feeds] instantly and the new order is committed to
 * [PinnedFeedsRepository.reorderPinnedFeeds] only on screen-exit / app-background,
 * via the injected [ApplicationScope] so the write survives `viewModelScope`
 * cancellation on a nav pop. [ManageFeedsEvent.Remove] unpins immediately
 * (non-destructive) and optimistically drops the row, rolling back on failure.
 *
 * Upstream `observePinnedFeeds` emissions re-seed the list only while the user has
 * NOT reordered (no pending local edit), so a background refresh can't stomp an
 * in-progress drag.
 */
@HiltViewModel
internal class ManageFeedsViewModel
    @Inject
    constructor(
        private val repository: PinnedFeedsRepository,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) : MviViewModel<ManageFeedsState, ManageFeedsEvent, ManageFeedsEffect>(ManageFeedsState()) {
        // The persisted pinned order (URIs) as last seeded/committed. The local list is
        // "dirty" (has an uncommitted reorder) when it differs from this.
        private var seededOrder: List<String> = emptyList()

        init {
            repository
                .observePinnedFeeds()
                .onEach { result -> seedIfNotDirty(result.feeds) }
                .catch { throwable ->
                    Timber.tag(TAG).e(throwable, "observePinnedFeeds collector failed")
                }.launchIn(viewModelScope)

            viewModelScope.launch {
                runCatching { repository.refresh() }
            }
        }

        override fun handleEvent(event: ManageFeedsEvent) {
            when (event) {
                is ManageFeedsEvent.Move -> onMove(event.from, event.to)
                is ManageFeedsEvent.Remove -> onRemove(event.uri)
            }
        }

        /** Commits the current local order if it differs from the last seeded order.
         * Called from [onCleared] (nav pop / destroy) and the screen's `ON_STOP`
         * (app-background) observer. Idempotent: a committed order updates [seededOrder]
         * so a second call with the same order is a no-op. */
        fun commitReorderIfDirty() {
            val content = uiState.value.status as? ManageFeedsLoadStatus.Content ?: return
            val order = content.feeds.map { it.uri }
            if (order == seededOrder) return
            applicationScope.launch { repository.reorderPinnedFeeds(order) }
            seededOrder = order
        }

        override fun onCleared() {
            commitReorderIfDirty()
            super.onCleared()
        }

        private fun onMove(
            from: Int,
            to: Int,
        ) {
            setState {
                val content = status as? ManageFeedsLoadStatus.Content ?: return@setState this
                if (from !in content.feeds.indices || to !in content.feeds.indices) return@setState this
                val list = content.feeds.toMutableList()
                list.add(to, list.removeAt(from))
                copy(status = ManageFeedsLoadStatus.Content(list.toImmutableList()))
            }
        }

        private fun onRemove(uri: String) {
            val content = uiState.value.status as? ManageFeedsLoadStatus.Content ?: return
            val index = content.feeds.indexOfFirst { it.uri == uri }
            if (index < 0) return
            val removed = content.feeds[index]
            val seedIndex = seededOrder.indexOf(uri)

            // Optimistic remove — local list AND seeded order, so a pure remove (no drag)
            // is not a dirty reorder and won't trigger a redundant reorder commit on exit.
            setState {
                copy(
                    status =
                        ManageFeedsLoadStatus.Content(
                            content.feeds
                                .toMutableList()
                                .apply { removeAt(index) }
                                .toImmutableList(),
                        ),
                )
            }
            if (seedIndex >= 0) seededOrder = seededOrder.toMutableList().apply { removeAt(seedIndex) }

            viewModelScope.launch {
                if (repository.unpinFeed(uri).isFailure) {
                    // Rollback: restore the row at its original index and surface the error.
                    setState {
                        val c = status as? ManageFeedsLoadStatus.Content ?: return@setState this
                        val list = c.feeds.toMutableList()
                        list.add(index.coerceAtMost(list.size), removed)
                        copy(status = ManageFeedsLoadStatus.Content(list.toImmutableList()))
                    }
                    if (seedIndex >= 0) {
                        seededOrder = seededOrder.toMutableList().apply { add(seedIndex.coerceAtMost(size), uri) }
                    }
                    sendEffect(ManageFeedsEffect.ShowRemoveError)
                }
            }
        }

        private fun seedIfNotDirty(feeds: ImmutableList<PinnedFeedUi>) {
            val current = (uiState.value.status as? ManageFeedsLoadStatus.Content)?.feeds
            // Only (re)seed when there is no pending local reorder — otherwise an upstream
            // emission (e.g. a background refresh) would stomp the in-progress drag.
            if (current != null && current.map { it.uri } != seededOrder) return
            setState { copy(status = ManageFeedsLoadStatus.Content(feeds)) }
            seededOrder = feeds.map { it.uri }
        }

        private companion object {
            const val TAG = "ManageFeedsVM"
        }
    }
