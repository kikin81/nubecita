package net.kikin.nubecita.feature.composer.impl

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.klipy.KlipyPagingSource
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUi
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerEffect
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerEvent
import net.kikin.nubecita.feature.composer.impl.state.KlipyPickerState
import javax.inject.Inject

/**
 * Drives the KLIPY picker: the paged GIF/sticker grid plus search, tabs,
 * categories, preview, and view/share/report tracking.
 *
 * The grid is a `Flow<PagingData<KlipyMediaUi>>` ([media]) rebuilt from an
 * internal [QueryKey] StateFlow whenever the tab, category, or (debounced)
 * search text changes — `flatMapLatest` cancels the previous feed and
 * `cachedIn` keeps it alive across recomposition. Free-text search is debounced
 * (~300ms) so a keystroke doesn't fire a request per character; tab/category
 * changes are immediate.
 *
 * Selection emits [KlipyPickerEffect.GifSelected] (the host attaches + closes);
 * the fire-and-forget `trackView`/`trackShare` run on an application scope in
 * the repository, so they survive the composer closing.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
internal class KlipyPickerViewModel
    @Inject
    constructor(
        private val klipyRepository: KlipyRepository,
    ) : MviViewModel<KlipyPickerState, KlipyPickerEvent, KlipyPickerEffect>(KlipyPickerState()) {
        private data class QueryKey(
            val type: KlipyMediaType = KlipyMediaType.GIF,
            val query: String = "",
            val recents: Boolean = false,
        )

        private val queryKey = MutableStateFlow(QueryKey())

        private val searchInput =
            MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        val media: Flow<PagingData<KlipyMediaUi>> =
            queryKey
                // StateFlow already de-dupes equal consecutive keys, so a repeated
                // tab/category/search key won't needlessly rebuild the Pager.
                .flatMapLatest { key ->
                    Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
                        KlipyPagingSource { page -> key.fetch(page) }
                    }.flow
                }.cachedIn(viewModelScope)

        init {
            loadCategories(KlipyMediaType.GIF)
            searchInput
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .onEach { query -> queryKey.update { it.copy(query = query, recents = false) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: KlipyPickerEvent) {
            when (event) {
                is KlipyPickerEvent.QueryChanged -> onQueryChanged(event.query)
                is KlipyPickerEvent.TabSelected -> onTabSelected(event.tab)
                is KlipyPickerEvent.CategorySelected -> onCategorySelected(event.category)
                is KlipyPickerEvent.ItemPreviewed -> {
                    klipyRepository.trackView(uiState.value.tab, event.media.slug)
                    setState { copy(preview = event.media) }
                }
                KlipyPickerEvent.PreviewDismissed -> setState { copy(preview = null) }
                is KlipyPickerEvent.ItemSelected -> {
                    klipyRepository.trackShare(uiState.value.tab, event.media.slug)
                    sendEffect(KlipyPickerEffect.GifSelected(event.media))
                }
                is KlipyPickerEvent.ItemReported -> onReport(event.media, event.reason)
            }
        }

        private fun onQueryChanged(query: String) {
            setState { copy(query = query, selectedCategory = null) }
            searchInput.tryEmit(query)
        }

        private fun onTabSelected(tab: KlipyMediaType) {
            if (tab == uiState.value.tab) return
            setState { copy(tab = tab, query = "", selectedCategory = null, categories = persistentListOf()) }
            queryKey.value = QueryKey(type = tab)
            loadCategories(tab)
        }

        private fun onCategorySelected(category: String) {
            setState { copy(selectedCategory = category, query = "") }
            val type = uiState.value.tab
            queryKey.value =
                when (category) {
                    RECENTS -> QueryKey(type, query = "", recents = true)
                    TRENDING -> QueryKey(type, query = "", recents = false)
                    else -> QueryKey(type, query = category, recents = false)
                }
        }

        private fun onReport(
            media: KlipyMediaUi,
            reason: KlipyReportReason,
        ) {
            setState { copy(preview = null) }
            viewModelScope.launch {
                val result = klipyRepository.report(uiState.value.tab, media.slug, reason)
                sendEffect(KlipyPickerEffect.ReportCompleted(succeeded = result.isSuccess))
            }
        }

        private fun loadCategories(type: KlipyMediaType) {
            viewModelScope.launch {
                klipyRepository.categories(type).onSuccess { categories ->
                    setState {
                        copy(
                            categories = categories,
                            selectedCategory = categories.firstOrNull { it == TRENDING } ?: categories.firstOrNull(),
                        )
                    }
                }
            }
        }

        private suspend fun QueryKey.fetch(page: Int): Result<KlipyMediaPage> = if (recents) klipyRepository.recents(type, page) else klipyRepository.search(type, query, page)

        internal companion object {
            const val SEARCH_DEBOUNCE_MS = 300L
            const val PAGE_SIZE = 30
            const val RECENTS = "Recents"
            const val TRENDING = "Trending"
        }
    }
