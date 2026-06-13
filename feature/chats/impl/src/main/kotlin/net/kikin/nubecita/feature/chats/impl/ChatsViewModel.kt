package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatsError
import javax.inject.Inject

/**
 * Presenter for the Chats tab home.
 *
 * Items come from the repository's reactive convo cache ([ChatRepository.observeConvos]) —
 * the single source of truth shared with the thread screen. The screen's
 * [ChatsLoadStatus] is `combine`d from that cache and a local [refreshPhase]
 * lifecycle: the cache supplies the list, the phase supplies loading /
 * refreshing / initial-error. Because the cache is shared and hot, sending a
 * message from a thread (which patches the cache) updates this list live without
 * a refetch.
 *
 * [Refresh] / [RetryClicked] trigger [ChatRepository.refreshConvos], single-flighted so
 * rapid pull-to-refresh can't fan out into concurrent requests.
 */
@HiltViewModel
class ChatsViewModel
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : MviViewModel<ChatsScreenViewState, ChatsEvent, ChatsEffect>(ChatsScreenViewState()) {
        // Accepted convos + pending requests are separate caches/lifecycles. The
        // ACTIVE segment decides which (items × phase) drives [status]; the request
        // count always feeds the Requests pill badge. Captured once so the VM never
        // depends on observe*() returning the same instance per call.
        private val acceptedConvos = repository.observeConvos()
        private val requestConvos = repository.observeRequestConvos()
        private val acceptedPhase = MutableStateFlow<RefreshPhase>(RefreshPhase.InitialLoading)
        private val requestPhase = MutableStateFlow<RefreshPhase>(RefreshPhase.InitialLoading)
        private val activeSegment = MutableStateFlow(ChatsSegment.Chats)
        private var refreshJob: Job? = null

        init {
            // Project (active segment's items × phase) into status, and the request
            // count into the badge. Both caches are the source of truth for items;
            // the per-segment phase drives loading / refreshing / error.
            combine(
                acceptedConvos,
                requestConvos,
                acceptedPhase,
                requestPhase,
                activeSegment,
            ) { accepted, requests, aPhase, rPhase, segment ->
                val items = if (segment == ChatsSegment.Chats) accepted else requests
                val phase = if (segment == ChatsSegment.Chats) aPhase else rPhase
                ChatsScreenViewState(
                    status = statusFor(items, phase),
                    activeSegment = segment,
                    requestCount = requests?.size ?: 0,
                )
            }.onEach { next -> setState { next } }
                .launchIn(viewModelScope)

            refresh()
        }

        override fun handleEvent(event: ChatsEvent) {
            when (event) {
                ChatsEvent.Refresh -> refresh()
                ChatsEvent.RetryClicked -> refresh()
                is ChatsEvent.ConvoTapped -> sendEffect(ChatsEffect.NavigateToChat(event.otherUserDid))
                ChatsEvent.SettingsTapped -> sendEffect(ChatsEffect.NavigateToChatSettings)
                is ChatsEvent.SegmentSelected -> activeSegment.value = event.segment
            }
        }

        private fun statusFor(
            items: ImmutableList<ConvoListItemUi>?,
            phase: RefreshPhase,
        ): ChatsLoadStatus =
            when {
                items != null ->
                    ChatsLoadStatus.Loaded(items = items, isRefreshing = phase == RefreshPhase.Refreshing)
                phase is RefreshPhase.InitialError -> ChatsLoadStatus.InitialError(phase.error)
                else -> ChatsLoadStatus.Loading
            }

        private fun refresh() {
            if (refreshJob?.isActive == true) return // single-flight on rapid Refresh.
            // Refreshing over an existing list shows the spinner atop it; a refresh
            // with no items yet is the initial load. Tracked per segment.
            acceptedPhase.value = phaseForRefreshStart(acceptedConvos.value != null)
            requestPhase.value = phaseForRefreshStart(requestConvos.value != null)
            refreshJob =
                viewModelScope.launch {
                    // Independent lists → fetch concurrently; one round-trip of latency.
                    val accepted = async { repository.refreshConvos() }
                    val requests = async { repository.refreshRequestConvos() }
                    // Accepted failure surfaces a transient snackbar (the primary list);
                    // a request failure stays inline in the Requests segment (no snackbar).
                    applyRefresh(accepted.await(), acceptedPhase, acceptedConvos.value != null, surfaceSnackbar = true)
                    applyRefresh(requests.await(), requestPhase, requestConvos.value != null, surfaceSnackbar = false)
                }
        }

        private fun phaseForRefreshStart(hasItems: Boolean): RefreshPhase = if (hasItems) RefreshPhase.Refreshing else RefreshPhase.InitialLoading

        private fun applyRefresh(
            result: Result<Unit>,
            phase: MutableStateFlow<RefreshPhase>,
            hadItems: Boolean,
            surfaceSnackbar: Boolean,
        ) {
            result
                .onSuccess { phase.value = RefreshPhase.Idle }
                .onFailure { throwable ->
                    val error = throwable.toChatsError()
                    if (hadItems) {
                        // Failure with items present: keep them, drop the indicator.
                        phase.value = RefreshPhase.Idle
                        if (surfaceSnackbar) sendEffect(ChatsEffect.ShowRefreshError(error))
                    } else {
                        phase.value = RefreshPhase.InitialError(error)
                    }
                }
        }

        /** Lifecycle of the refresh request, combined with the cache to form the screen status. */
        private sealed interface RefreshPhase {
            data object InitialLoading : RefreshPhase

            data object Refreshing : RefreshPhase

            data object Idle : RefreshPhase

            data class InitialError(
                val error: ChatsError,
            ) : RefreshPhase
        }
    }
